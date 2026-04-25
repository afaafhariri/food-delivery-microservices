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

	_ "github.com/quickbite/delivery-service/docs"
	"github.com/quickbite/delivery-service/internal/config"
	"github.com/quickbite/delivery-service/internal/handler"
	"github.com/quickbite/delivery-service/internal/kafka"
	"github.com/quickbite/delivery-service/internal/middleware"
	"github.com/quickbite/delivery-service/internal/repository"
	"github.com/quickbite/delivery-service/internal/service"
)

// @title Delivery Service API
// @version 1.0
// @description API for managing deliveries and drivers in QuickBite
// @BasePath /
func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo})))

	cfg := config.Load()

	db, err := gorm.Open(postgres.Open(cfg.DSN()), &gorm.Config{})
	if err != nil {
		slog.Error("failed to connect to database", "error", err)
		os.Exit(1)
	}
	slog.Info("connected to database")

	runMigrations(cfg)

	driverRepo := repository.NewDriverRepository(db)
	deliveryRepo := repository.NewDeliveryRepository(db)

	producer := kafka.NewDeliveryEventProducer(cfg.KafkaBrokers)
	defer producer.Close()

	driverService := service.NewDriverService(driverRepo)
	deliveryService := service.NewDeliveryService(deliveryRepo, driverRepo, producer)

	driverHandler := handler.NewDriverHandler(driverService)
	deliveryHandler := handler.NewDeliveryHandler(deliveryService)

	r := chi.NewRouter()

	r.Use(middleware.RequestID)
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Use(middleware.JSONContentType)

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"ok"}`))
	})

	r.Get("/swagger/*", httpSwagger.Handler(
		httpSwagger.URL("/swagger/doc.json"),
	))

	r.Route("/api", func(r chi.Router) {
		r.Post("/drivers", driverHandler.RegisterDriver)
		r.Get("/drivers", driverHandler.ListDrivers)
		r.Put("/drivers/{id}", driverHandler.UpdateDriver)

		r.Get("/deliveries", deliveryHandler.ListDeliveries)
		r.Get("/deliveries/{id}", deliveryHandler.GetDelivery)
		r.Patch("/deliveries/{id}/status", deliveryHandler.UpdateDeliveryStatus)
		r.Get("/deliveries/order/{orderId}", deliveryHandler.GetDeliveryByOrderID)
	})

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	consumer := kafka.NewOrderPlacedConsumer(cfg.KafkaBrokers, driverRepo, deliveryRepo, producer)
	go consumer.Start(ctx)
	defer consumer.Close()

	server := &http.Server{
		Addr:         ":" + cfg.ServerPort,
		Handler:      r,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		slog.Info("starting delivery service", "port", cfg.ServerPort)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
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

	if err := server.Shutdown(shutdownCtx); err != nil {
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
