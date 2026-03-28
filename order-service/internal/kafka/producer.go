package kafka

import (
	"context"
	"encoding/json"
	"log/slog"
	"strings"
	"time"

	"github.com/segmentio/kafka-go"
)

type OrderPlacedEvent struct {
	OrderID         string           `json:"orderId"`
	CustomerID      string           `json:"customerId"`
	RestaurantID    string           `json:"restaurantId"`
	Items           []OrderItemEvent `json:"items"`
	DeliveryAddress string           `json:"deliveryAddress"`
	Timestamp       time.Time        `json:"timestamp"`
}

type OrderItemEvent struct {
	MenuItemID   string  `json:"menuItemId"`
	MenuItemName string  `json:"menuItemName"`
	Quantity     int     `json:"quantity"`
	UnitPrice    float64 `json:"unitPrice"`
}

type OrderEventProducer struct {
	writer *kafka.Writer
}

func NewOrderEventProducer(brokers string) *OrderEventProducer {
	w := &kafka.Writer{
		Addr:         kafka.TCP(strings.Split(brokers, ",")...),
		Topic:        "order.placed",
		Balancer:     &kafka.LeastBytes{},
		BatchTimeout: 10 * time.Millisecond,
	}
	return &OrderEventProducer{writer: w}
}

func (p *OrderEventProducer) PublishOrderPlaced(ctx context.Context, event OrderPlacedEvent) error {
	data, err := json.Marshal(event)
	if err != nil {
		return err
	}

	msg := kafka.Message{
		Key:   []byte(event.OrderID),
		Value: data,
	}

	if err := p.writer.WriteMessages(ctx, msg); err != nil {
		slog.Error("failed to publish order.placed event", "error", err, "orderId", event.OrderID)
		return err
	}

	slog.Info("published order.placed event", "orderId", event.OrderID)
	return nil
}

func (p *OrderEventProducer) Close() error {
	return p.writer.Close()
}
