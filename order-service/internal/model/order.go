package model

import (
	"time"
)

const (
	StatusPlaced    = "PLACED"
	StatusConfirmed = "CONFIRMED"
	StatusPreparing = "PREPARING"
	StatusReady     = "READY"
	StatusPickedUp  = "PICKED_UP"
	StatusDelivered = "DELIVERED"
	StatusCancelled = "CANCELLED"
)

type Order struct {
	ID              string      `gorm:"type:uuid;primaryKey" json:"id"`
	CustomerID      string      `gorm:"type:uuid;not null;index" json:"customer_id"`
	RestaurantID    string      `gorm:"type:uuid;not null" json:"restaurant_id"`
	DeliveryAddress string      `gorm:"type:text;not null" json:"delivery_address"`
	Status          string      `gorm:"type:varchar(20);not null;default:'PLACED';index" json:"status"`
	TotalAmount     float64     `gorm:"type:decimal(10,2);not null;default:0" json:"total_amount"`
	CreatedAt       time.Time   `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt       time.Time   `gorm:"autoUpdateTime" json:"updated_at"`
	Items           []OrderItem `gorm:"foreignKey:OrderID;constraint:OnDelete:CASCADE" json:"items"`
}

func (Order) TableName() string {
	return "orders"
}

type OrderItem struct {
	ID           string  `gorm:"type:uuid;primaryKey" json:"id"`
	OrderID      string  `gorm:"type:uuid;not null" json:"order_id"`
	MenuItemID   string  `gorm:"type:uuid;not null" json:"menu_item_id"`
	MenuItemName string  `gorm:"type:varchar(255);not null" json:"menu_item_name"`
	Quantity     int     `gorm:"not null" json:"quantity"`
	UnitPrice    float64 `gorm:"type:decimal(10,2);not null" json:"unit_price"`
}

func (OrderItem) TableName() string {
	return "order_items"
}
