package service

import (
	"fmt"

	"github.com/google/uuid"
	"github.com/quickbite/delivery-service/internal/dto"
	"github.com/quickbite/delivery-service/internal/model"
	"github.com/quickbite/delivery-service/internal/repository"
)

type DriverService struct {
	repo *repository.DriverRepository
}

func NewDriverService(repo *repository.DriverRepository) *DriverService {
	return &DriverService{repo: repo}
}

func (s *DriverService) Create(req dto.CreateDriverRequest) (*dto.DriverResponse, error) {
	if req.Name == "" || req.Phone == "" || req.VehicleType == "" {
		return nil, fmt.Errorf("name, phone, and vehicle_type are required")
	}

	driver := &model.Driver{
		ID:          uuid.New().String(),
		Name:        req.Name,
		Phone:       req.Phone,
		VehicleType: req.VehicleType,
		Available:   true,
	}

	if err := s.repo.Create(driver); err != nil {
		return nil, fmt.Errorf("failed to create driver: %w", err)
	}

	return toDriverResponse(driver), nil
}

func (s *DriverService) GetDriver(id string) (*dto.DriverResponse, error) {
	driver, err := s.repo.FindByID(id)
	if err != nil {
		return nil, fmt.Errorf("driver not found: %w", err)
	}
	return toDriverResponse(driver), nil
}

func (s *DriverService) ListDrivers(available *bool) ([]dto.DriverResponse, error) {
	drivers, err := s.repo.FindAll(available)
	if err != nil {
		return nil, fmt.Errorf("failed to list drivers: %w", err)
	}

	responses := make([]dto.DriverResponse, len(drivers))
	for i, d := range drivers {
		responses[i] = *toDriverResponse(&d)
	}
	return responses, nil
}

func (s *DriverService) UpdateDriver(id string, req dto.UpdateDriverRequest) (*dto.DriverResponse, error) {
	driver, err := s.repo.FindByID(id)
	if err != nil {
		return nil, fmt.Errorf("driver not found: %w", err)
	}

	if req.Name != "" {
		driver.Name = req.Name
	}
	if req.Phone != "" {
		driver.Phone = req.Phone
	}
	if req.VehicleType != "" {
		driver.VehicleType = req.VehicleType
	}
	if req.Available != nil {
		driver.Available = *req.Available
	}

	if err := s.repo.Update(driver); err != nil {
		return nil, fmt.Errorf("failed to update driver: %w", err)
	}

	return toDriverResponse(driver), nil
}

func toDriverResponse(d *model.Driver) *dto.DriverResponse {
	return &dto.DriverResponse{
		ID:          d.ID,
		Name:        d.Name,
		Phone:       d.Phone,
		VehicleType: d.VehicleType,
		Available:   d.Available,
		CreatedAt:   d.CreatedAt,
		UpdatedAt:   d.UpdatedAt,
	}
}
