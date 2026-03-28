package kafka

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	"github.com/google/uuid"
	"github.com/quickbite/delivery-service/internal/model"
	"github.com/quickbite/delivery-service/internal/repository"
	kafkago "github.com/segmentio/kafka-go"
)

type OrderPlacedEvent struct {
	OrderID string `json:"orderId"`
}

type OrderPlacedConsumer struct {
	reader       *kafkago.Reader
	driverRepo   *repository.DriverRepository
	deliveryRepo *repository.DeliveryRepository
	producer     *DeliveryEventProducer
}

func NewOrderPlacedConsumer(
	brokers []string,
	driverRepo *repository.DriverRepository,
	deliveryRepo *repository.DeliveryRepository,
	producer *DeliveryEventProducer,
) *OrderPlacedConsumer {
	reader := kafkago.NewReader(kafkago.ReaderConfig{
		Brokers:        brokers,
		Topic:          "order.placed",
		GroupID:        "delivery-service-group",
		MinBytes:       1,
		MaxBytes:       10e6,
		CommitInterval: time.Second,
		StartOffset:    kafkago.LastOffset,
	})

	return &OrderPlacedConsumer{
		reader:       reader,
		driverRepo:   driverRepo,
		deliveryRepo: deliveryRepo,
		producer:     producer,
	}
}

func (c *OrderPlacedConsumer) Start(ctx context.Context) {
	slog.Info("starting order.placed consumer")

	for {
		select {
		case <-ctx.Done():
			slog.Info("stopping order.placed consumer")
			return
		default:
		}

		msg, err := c.readMessageWithRetry(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			slog.Error("failed to read message after retries", "error", err)
			continue
		}

		c.handleMessage(ctx, msg)
	}
}

func (c *OrderPlacedConsumer) readMessageWithRetry(ctx context.Context) (kafkago.Message, error) {
	backoff := time.Second
	maxBackoff := 30 * time.Second

	for {
		msg, err := c.reader.ReadMessage(ctx)
		if err == nil {
			return msg, nil
		}

		if ctx.Err() != nil {
			return kafkago.Message{}, ctx.Err()
		}

		slog.Warn("failed to read from Kafka, retrying", "error", err, "backoff", backoff)

		select {
		case <-ctx.Done():
			return kafkago.Message{}, ctx.Err()
		case <-time.After(backoff):
		}

		backoff *= 2
		if backoff > maxBackoff {
			backoff = maxBackoff
		}
	}
}

func (c *OrderPlacedConsumer) handleMessage(ctx context.Context, msg kafkago.Message) {
	var event OrderPlacedEvent
	if err := json.Unmarshal(msg.Value, &event); err != nil {
		slog.Error("failed to unmarshal order.placed event", "error", err)
		return
	}

	slog.Info("received order.placed event", "orderId", event.OrderID)

	if event.OrderID == "" {
		slog.Error("order.placed event missing orderId")
		return
	}

	driver, err := c.driverRepo.FindNextAvailable()
	if err != nil {
		slog.Error("no available driver for order", "orderId", event.OrderID, "error", err)
		return
	}

	delivery := &model.Delivery{
		ID:       uuid.New().String(),
		OrderID:  event.OrderID,
		DriverID: driver.ID,
		Status:   model.StatusAssigned,
	}

	if err := c.deliveryRepo.Create(delivery); err != nil {
		slog.Error("failed to create delivery", "orderId", event.OrderID, "error", err)
		return
	}

	driver.Available = false
	if err := c.driverRepo.Update(driver); err != nil {
		slog.Error("failed to update driver availability", "driverId", driver.ID, "error", err)
	}

	slog.Info("delivery assigned", "deliveryId", delivery.ID, "orderId", event.OrderID, "driverId", driver.ID)

	if c.producer != nil {
		c.producer.PublishStatusUpdate(delivery.ID, delivery.OrderID, driver.ID, string(model.StatusAssigned))
	}
}

func (c *OrderPlacedConsumer) Close() error {
	return c.reader.Close()
}
