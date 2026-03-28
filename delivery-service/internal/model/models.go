package model

import (
	"time"
)

type Driver struct {
	ID          string    `gorm:"type:uuid;primaryKey" json:"id"`
	Name        string    `gorm:"type:varchar(255);not null" json:"name"`
	Phone       string    `gorm:"type:varchar(20);not null" json:"phone"`
	VehicleType string    `gorm:"type:varchar(50);not null" json:"vehicle_type"`
	Available   bool      `gorm:"default:true" json:"available"`
	CreatedAt   time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt   time.Time `gorm:"autoUpdateTime" json:"updated_at"`
}

func (Driver) TableName() string {
	return "drivers"
}

type DeliveryStatus string

const (
	StatusAssigned  DeliveryStatus = "ASSIGNED"
	StatusPickedUp  DeliveryStatus = "PICKED_UP"
	StatusEnRoute   DeliveryStatus = "EN_ROUTE"
	StatusDelivered DeliveryStatus = "DELIVERED"
)

type Delivery struct {
	ID        string         `gorm:"type:uuid;primaryKey" json:"id"`
	OrderID   string         `gorm:"type:uuid;not null;uniqueIndex" json:"order_id"`
	DriverID  string         `gorm:"type:uuid" json:"driver_id"`
	Driver    Driver         `gorm:"foreignKey:DriverID" json:"driver"`
	Status    DeliveryStatus `gorm:"type:varchar(20);not null;default:'ASSIGNED'" json:"status"`
	CreatedAt time.Time      `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt time.Time      `gorm:"autoUpdateTime" json:"updated_at"`
}

func (Delivery) TableName() string {
	return "deliveries"
}

func IsValidDeliveryStatus(s string) bool {
	switch DeliveryStatus(s) {
	case StatusAssigned, StatusPickedUp, StatusEnRoute, StatusDelivered:
		return true
	}
	return false
}
