package service

import (
	"fmt"

	"github.com/google/uuid"
	"github.com/quickbite/delivery-service/internal/dto"
	"github.com/quickbite/delivery-service/internal/kafka"
	"github.com/quickbite/delivery-service/internal/model"
	"github.com/quickbite/delivery-service/internal/repository"
)

type DeliveryService struct {
	deliveryRepo *repository.DeliveryRepository
	driverRepo   *repository.DriverRepository
	producer     *kafka.DeliveryEventProducer
}

func NewDeliveryService(
	deliveryRepo *repository.DeliveryRepository,
	driverRepo *repository.DriverRepository,
	producer *kafka.DeliveryEventProducer,
) *DeliveryService {
	return &DeliveryService{
		deliveryRepo: deliveryRepo,
		driverRepo:   driverRepo,
		producer:     producer,
	}
}

func (s *DeliveryService) CreateDelivery(orderID string) (*dto.DeliveryResponse, error) {
	driver, err := s.driverRepo.FindNextAvailable()
	if err != nil {
		return nil, fmt.Errorf("no available driver: %w", err)
	}

	delivery := &model.Delivery{
		ID:       uuid.New().String(),
		OrderID:  orderID,
		DriverID: driver.ID,
		Status:   model.StatusAssigned,
	}

	if err := s.deliveryRepo.Create(delivery); err != nil {
		return nil, fmt.Errorf("failed to create delivery: %w", err)
	}

	driver.Available = false
	if err := s.driverRepo.Update(driver); err != nil {
		return nil, fmt.Errorf("failed to update driver availability: %w", err)
	}

	delivery.Driver = *driver

	if s.producer != nil {
		s.producer.PublishStatusUpdate(delivery.ID, delivery.OrderID, delivery.DriverID, string(delivery.Status))
	}

	return toDeliveryResponse(delivery), nil
}

func (s *DeliveryService) GetDelivery(id string) (*dto.DeliveryResponse, error) {
	delivery, err := s.deliveryRepo.FindByID(id)
	if err != nil {
		return nil, fmt.Errorf("delivery not found: %w", err)
	}
	return toDeliveryResponse(delivery), nil
}

func (s *DeliveryService) ListDeliveries(driverID, status string) ([]dto.DeliveryResponse, error) {
	deliveries, err := s.deliveryRepo.FindAll(driverID, status)
	if err != nil {
		return nil, fmt.Errorf("failed to list deliveries: %w", err)
	}

	responses := make([]dto.DeliveryResponse, len(deliveries))
	for i, d := range deliveries {
		responses[i] = *toDeliveryResponse(&d)
	}
	return responses, nil
}

func (s *DeliveryService) GetDeliveryByOrderID(orderID string) (*dto.DeliveryResponse, error) {
	delivery, err := s.deliveryRepo.FindByOrderID(orderID)
	if err != nil {
		return nil, fmt.Errorf("delivery not found for order: %w", err)
	}
	return toDeliveryResponse(delivery), nil
}

func (s *DeliveryService) UpdateDeliveryStatus(id string, status string) (*dto.DeliveryResponse, error) {
	if !model.IsValidDeliveryStatus(status) {
		return nil, fmt.Errorf("invalid delivery status: %s", status)
	}

	delivery, err := s.deliveryRepo.FindByID(id)
	if err != nil {
		return nil, fmt.Errorf("delivery not found: %w", err)
	}

	delivery.Status = model.DeliveryStatus(status)

	if err := s.deliveryRepo.Update(delivery); err != nil {
		return nil, fmt.Errorf("failed to update delivery status: %w", err)
	}

	if delivery.Status == model.StatusDelivered {
		driver, err := s.driverRepo.FindByID(delivery.DriverID)
		if err == nil {
			driver.Available = true
			_ = s.driverRepo.Update(driver)
		}
	}

	if s.producer != nil {
		s.producer.PublishStatusUpdate(delivery.ID, delivery.OrderID, delivery.DriverID, string(delivery.Status))
	}

	return toDeliveryResponse(delivery), nil
}

func toDeliveryResponse(d *model.Delivery) *dto.DeliveryResponse {
	resp := &dto.DeliveryResponse{
		ID:        d.ID,
		OrderID:   d.OrderID,
		DriverID:  d.DriverID,
		Status:    string(d.Status),
		CreatedAt: d.CreatedAt,
		UpdatedAt: d.UpdatedAt,
	}

	if d.Driver.ID != "" {
		resp.Driver = &dto.DriverResponse{
			ID:          d.Driver.ID,
			Name:        d.Driver.Name,
			Phone:       d.Driver.Phone,
			VehicleType: d.Driver.VehicleType,
			Available:   d.Driver.Available,
			CreatedAt:   d.Driver.CreatedAt,
			UpdatedAt:   d.Driver.UpdatedAt,
		}
	}

	return resp
}
