// @title       Order Service API
// @version     1.0
// @description QuickBite Order Service API
// @BasePath    /api

package main

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/golang-migrate/migrate/v4"
	_ "github.com/golang-migrate/migrate/v4/database/postgres"
	_ "github.com/golang-migrate/migrate/v4/source/file"
	httpSwagger "github.com/swaggo/http-swagger/v2"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"

	_ "github.com/quickbite/order-service/docs"
	"github.com/quickbite/order-service/internal/config"
	"github.com/quickbite/order-service/internal/handler"
	"github.com/quickbite/order-service/internal/kafka"
	"github.com/quickbite/order-service/internal/middleware"
	"github.com/quickbite/order-service/internal/repository"
	"github.com/quickbite/order-service/internal/service"
)

func main() {
	cfg := config.Load()

	db, err := connectDB(cfg)
	if err != nil {
		slog.Error("failed to connect to database", "error", err)
		os.Exit(1)
	}

	slog.Info("connected to database")

	runMigrations(cfg)

	producer := kafka.NewOrderEventProducer(cfg.KafkaBrokers)
	defer producer.Close()

	repo := repository.NewOrderRepository(db)
	svc := service.NewOrderService(repo, producer)
	h := handler.NewOrderHandler(svc)

	r := chi.NewRouter()
	r.Use(func(next http.Handler) http.Handler {
		return middleware.Setup(next)
	})

	h.RegisterRoutes(r)

	r.Get("/swagger/*", httpSwagger.Handler(
		httpSwagger.URL("/swagger/doc.json"),
	))

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"ok"}`))
	})

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go kafka.StartDeliveryStatusConsumer(ctx, cfg.KafkaBrokers, db)
	go kafka.StartRestaurantResponseConsumer(ctx, cfg.KafkaBrokers, db)

	srv := &http.Server{
		Addr:         fmt.Sprintf(":%s", cfg.ServerPort),
		Handler:      r,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		slog.Info("starting order service", "port", cfg.ServerPort)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("server failed", "error", err)
			os.Exit(1)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	slog.Info("shutting down server")
	cancel()

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer shutdownCancel()

	if err := srv.Shutdown(shutdownCtx); err != nil {
		slog.Error("server forced to shutdown", "error", err)
	}

	slog.Info("server stopped")
}

func runMigrations(cfg *config.Config) {
	dsn := fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=disable",
		cfg.DBUser, cfg.DBPassword, cfg.DBHost, cfg.DBPort, cfg.DBName)

	m, err := migrate.New("file://migrations", dsn)
	if err != nil {
		slog.Error("failed to create migration instance", "error", err)
		return
	}
	defer m.Close()

	if err := m.Up(); err != nil && err != migrate.ErrNoChange {
		slog.Error("failed to run migrations", "error", err)
		return
	}

	slog.Info("migrations completed successfully")
}

func connectDB(cfg *config.Config) (*gorm.DB, error) {
	var db *gorm.DB
	var err error

	for i := 0; i < 5; i++ {
		db, err = gorm.Open(postgres.Open(cfg.DSN()), &gorm.Config{})
		if err == nil {
			return db, nil
		}
		slog.Warn("failed to connect to database, retrying", "attempt", i+1, "error", err)
		time.Sleep(time.Duration(i+1) * 2 * time.Second)
	}

	return nil, fmt.Errorf("failed to connect to database after 5 attempts: %w", err)
}
