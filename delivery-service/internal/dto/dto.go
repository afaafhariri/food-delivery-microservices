package dto

import "time"

type CreateDriverRequest struct {
	Name        string `json:"name" example:"John Doe"`
	Phone       string `json:"phone" example:"+1234567890"`
	VehicleType string `json:"vehicle_type" example:"motorcycle"`
}

type UpdateDriverRequest struct {
	Name        string `json:"name" example:"John Doe"`
	Phone       string `json:"phone" example:"+1234567890"`
	VehicleType string `json:"vehicle_type" example:"motorcycle"`
	Available   *bool  `json:"available" example:"true"`
}

type DriverResponse struct {
	ID          string    `json:"id" example:"550e8400-e29b-41d4-a716-446655440000"`
	Name        string    `json:"name" example:"John Doe"`
	Phone       string    `json:"phone" example:"+1234567890"`
	VehicleType string    `json:"vehicle_type" example:"motorcycle"`
	Available   bool      `json:"available" example:"true"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

type DeliveryResponse struct {
	ID        string          `json:"id" example:"550e8400-e29b-41d4-a716-446655440000"`
	OrderID   string          `json:"order_id" example:"660e8400-e29b-41d4-a716-446655440000"`
	DriverID  string          `json:"driver_id" example:"770e8400-e29b-41d4-a716-446655440000"`
	Driver    *DriverResponse `json:"driver,omitempty"`
	Status    string          `json:"status" example:"ASSIGNED"`
	CreatedAt time.Time       `json:"created_at"`
	UpdatedAt time.Time       `json:"updated_at"`
}

type UpdateDeliveryStatusRequest struct {
	Status string `json:"status" example:"PICKED_UP"`
}

type ErrorResponse struct {
	Message string `json:"message" example:"resource not found"`
	Status  int    `json:"status" example:"404"`
}
