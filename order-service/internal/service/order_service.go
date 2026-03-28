package service

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/quickbite/order-service/internal/dto"
	"github.com/quickbite/order-service/internal/kafka"
	"github.com/quickbite/order-service/internal/model"
	"github.com/quickbite/order-service/internal/repository"
)

type OrderService struct {
	repo     *repository.OrderRepository
	producer *kafka.OrderEventProducer
}

func NewOrderService(repo *repository.OrderRepository, producer *kafka.OrderEventProducer) *OrderService {
	return &OrderService{
		repo:     repo,
		producer: producer,
	}
}

func (s *OrderService) CreateOrder(req dto.CreateOrderRequest) (*dto.OrderResponse, error) {
	if req.CustomerID == "" || req.RestaurantID == "" || req.DeliveryAddress == "" {
		return nil, errors.New("customer_id, restaurant_id, and delivery_address are required")
	}
	if len(req.Items) == 0 {
		return nil, errors.New("at least one item is required")
	}

	orderID := uuid.New().String()
	var totalAmount float64
	var items []model.OrderItem

	for _, item := range req.Items {
		itemTotal := float64(item.Quantity) * item.UnitPrice
		totalAmount += itemTotal
		items = append(items, model.OrderItem{
			ID:           uuid.New().String(),
			OrderID:      orderID,
			MenuItemID:   item.MenuItemID,
			MenuItemName: item.MenuItemName,
			Quantity:     item.Quantity,
			UnitPrice:    item.UnitPrice,
		})
	}

	order := &model.Order{
		ID:              orderID,
		CustomerID:      req.CustomerID,
		RestaurantID:    req.RestaurantID,
		DeliveryAddress: req.DeliveryAddress,
		Status:          model.StatusPlaced,
		TotalAmount:     totalAmount,
		Items:           items,
	}

	if err := s.repo.Create(order); err != nil {
		return nil, fmt.Errorf("failed to create order: %w", err)
	}

	var eventItems []kafka.OrderItemEvent
	for _, item := range req.Items {
		eventItems = append(eventItems, kafka.OrderItemEvent{
			MenuItemID:   item.MenuItemID,
			MenuItemName: item.MenuItemName,
			Quantity:     item.Quantity,
			UnitPrice:    item.UnitPrice,
		})
	}

	event := kafka.OrderPlacedEvent{
		OrderID:         orderID,
		CustomerID:      req.CustomerID,
		RestaurantID:    req.RestaurantID,
		Items:           eventItems,
		DeliveryAddress: req.DeliveryAddress,
		Timestamp:       time.Now(),
	}

	_ = s.producer.PublishOrderPlaced(context.Background(), event)

	return toOrderResponse(order), nil
}

func (s *OrderService) GetOrder(id string) (*dto.OrderResponse, error) {
	order, err := s.repo.FindByID(id)
	if err != nil {
		return nil, fmt.Errorf("order not found: %w", err)
	}
	return toOrderResponse(order), nil
}

func (s *OrderService) ListOrders(params dto.ListOrdersParams, page, size int) ([]dto.OrderResponse, int64, error) {
	if page < 1 {
		page = 1
	}
	if size < 1 || size > 100 {
		size = 20
	}

	orders, total, err := s.repo.FindAll(params, page, size)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to list orders: %w", err)
	}

	var responses []dto.OrderResponse
	for i := range orders {
		responses = append(responses, *toOrderResponse(&orders[i]))
	}

	return responses, total, nil
}

func (s *OrderService) UpdateStatus(id string, req dto.UpdateStatusRequest) (*dto.OrderResponse, error) {
	order, err := s.repo.FindByID(id)
	if err != nil {
		return nil, fmt.Errorf("order not found: %w", err)
	}

	validStatuses := map[string]bool{
		model.StatusPlaced:    true,
		model.StatusConfirmed: true,
		model.StatusPreparing: true,
		model.StatusReady:     true,
		model.StatusPickedUp:  true,
		model.StatusDelivered: true,
		model.StatusCancelled: true,
	}

	if !validStatuses[req.Status] {
		return nil, fmt.Errorf("invalid status: %s", req.Status)
	}

	order.Status = req.Status
	if err := s.repo.Update(order); err != nil {
		return nil, fmt.Errorf("failed to update order status: %w", err)
	}

	return toOrderResponse(order), nil
}

func (s *OrderService) CancelOrder(id string) (*dto.OrderResponse, error) {
	order, err := s.repo.FindByID(id)
	if err != nil {
		return nil, fmt.Errorf("order not found: %w", err)
	}

	if order.Status != model.StatusPlaced {
		return nil, fmt.Errorf("order can only be cancelled when status is PLACED, current status: %s", order.Status)
	}

	order.Status = model.StatusCancelled
	if err := s.repo.Update(order); err != nil {
		return nil, fmt.Errorf("failed to cancel order: %w", err)
	}

	return toOrderResponse(order), nil
}

func toOrderResponse(order *model.Order) *dto.OrderResponse {
	var items []dto.OrderItemResponse
	for _, item := range order.Items {
		items = append(items, dto.OrderItemResponse{
			ID:           item.ID,
			OrderID:      item.OrderID,
			MenuItemID:   item.MenuItemID,
			MenuItemName: item.MenuItemName,
			Quantity:     item.Quantity,
			UnitPrice:    item.UnitPrice,
		})
	}

	return &dto.OrderResponse{
		ID:              order.ID,
		CustomerID:      order.CustomerID,
		RestaurantID:    order.RestaurantID,
		DeliveryAddress: order.DeliveryAddress,
		Status:          order.Status,
		TotalAmount:     order.TotalAmount,
		CreatedAt:       order.CreatedAt,
		UpdatedAt:       order.UpdatedAt,
		Items:           items,
	}
}
