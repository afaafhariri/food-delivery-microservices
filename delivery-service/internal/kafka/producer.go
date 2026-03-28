package kafka

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	kafkago "github.com/segmentio/kafka-go"
)

type DeliveryStatusEvent struct {
	DeliveryID string `json:"deliveryId"`
	OrderID    string `json:"orderId"`
	DriverID   string `json:"driverId"`
	Status     string `json:"status"`
	Timestamp  string `json:"timestamp"`
}

type DeliveryEventProducer struct {
	writer *kafkago.Writer
}

func NewDeliveryEventProducer(brokers []string) *DeliveryEventProducer {
	writer := &kafkago.Writer{
		Addr:         kafkago.TCP(brokers...),
		Topic:        "delivery.status.updated",
		Balancer:     &kafkago.LeastBytes{},
		BatchTimeout: 10 * time.Millisecond,
	}

	return &DeliveryEventProducer{writer: writer}
}

func (p *DeliveryEventProducer) PublishStatusUpdate(deliveryID, orderID, driverID, status string) {
	event := DeliveryStatusEvent{
		DeliveryID: deliveryID,
		OrderID:    orderID,
		DriverID:   driverID,
		Status:     status,
		Timestamp:  time.Now().UTC().Format(time.RFC3339),
	}

	data, err := json.Marshal(event)
	if err != nil {
		slog.Error("failed to marshal delivery status event", "error", err)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	err = p.writer.WriteMessages(ctx, kafkago.Message{
		Key:   []byte(deliveryID),
		Value: data,
	})
	if err != nil {
		slog.Error("failed to publish delivery status event", "error", err, "deliveryId", deliveryID)
		return
	}

	slog.Info("published delivery status event", "deliveryId", deliveryID, "status", status)
}

func (p *DeliveryEventProducer) Close() error {
	return p.writer.Close()
}