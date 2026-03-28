package kafka

import (
	"context"
	"encoding/json"
	"log/slog"
	"strings"
	"time"

	"github.com/segmentio/kafka-go"
	"gorm.io/gorm"
)

type DeliveryStatusEvent struct {
	OrderID string `json:"orderId"`
	Status  string `json:"status"`
}

type RestaurantResponseEvent struct {
	OrderID   string `json:"orderId"`
	Status    string `json:"status"`
	Reason    string `json:"reason,omitempty"`
	Timestamp string `json:"timestamp"`
}

func StartDeliveryStatusConsumer(ctx context.Context, brokers string, db *gorm.DB) {
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:        strings.Split(brokers, ","),
		Topic:          "delivery.status.updated",
		GroupID:        "order-service-group",
		MinBytes:       1,
		MaxBytes:       10e6,
		CommitInterval: time.Second,
	})
	defer reader.Close()

	slog.Info("starting delivery status consumer")

	for {
		if err := consumeDeliveryMessages(ctx, reader, db); err != nil {
			if ctx.Err() != nil {
				slog.Info("delivery status consumer shutting down")
				return
			}
			slog.Error("delivery status consumer error, retrying in 5s", "error", err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(5 * time.Second):
			}
		}
	}
}

func consumeDeliveryMessages(ctx context.Context, reader *kafka.Reader, db *gorm.DB) error {
	for {
		msg, err := reader.ReadMessage(ctx)
		if err != nil {
			return err
		}

		var event DeliveryStatusEvent
		if err := json.Unmarshal(msg.Value, &event); err != nil {
			slog.Error("failed to unmarshal delivery status event", "error", err)
			continue
		}

		result := db.Table("orders").Where("id = ?", event.OrderID).Update("status", event.Status)
		if result.Error != nil {
			slog.Error("failed to update order status from delivery event", "error", result.Error, "orderId", event.OrderID)
			continue
		}

		slog.Info("updated order status from delivery event", "orderId", event.OrderID, "status", event.Status)
	}
}

func StartRestaurantResponseConsumer(ctx context.Context, brokers string, db *gorm.DB) {
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:        strings.Split(brokers, ","),
		Topic:          "restaurant.order.response",
		GroupID:        "order-service-group",
		MinBytes:       1,
		MaxBytes:       10e6,
		CommitInterval: time.Second,
	})
	defer reader.Close()

	slog.Info("starting restaurant response consumer")

	for {
		if err := consumeRestaurantMessages(ctx, reader, db); err != nil {
			if ctx.Err() != nil {
				slog.Info("restaurant response consumer shutting down")
				return
			}
			slog.Error("restaurant response consumer error, retrying in 5s", "error", err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(5 * time.Second):
			}
		}
	}
}

func consumeRestaurantMessages(ctx context.Context, reader *kafka.Reader, db *gorm.DB) error {
	for {
		msg, err := reader.ReadMessage(ctx)
		if err != nil {
			return err
		}

		var event RestaurantResponseEvent
		if err := json.Unmarshal(msg.Value, &event); err != nil {
			slog.Error("failed to unmarshal restaurant response event", "error", err)
			continue
		}

		status := "CANCELLED"
		if event.Status == "CONFIRMED" {
			status = "CONFIRMED"
		}

		result := db.Table("orders").Where("id = ?", event.OrderID).Update("status", status)
		if result.Error != nil {
			slog.Error("failed to update order status from restaurant response", "error", result.Error, "orderId", event.OrderID)
			continue
		}

		slog.Info("updated order status from restaurant response", "orderId", event.OrderID, "status", status)
	}
}
