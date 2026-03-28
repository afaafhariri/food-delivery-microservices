package dto

import "time"

type CreateOrderRequest struct {
	CustomerID      string             `json:"customer_id" example:"550e8400-e29b-41d4-a716-446655440000"`
	RestaurantID    string             `json:"restaurant_id" example:"660e8400-e29b-41d4-a716-446655440000"`
	Items           []OrderItemRequest `json:"items"`
	DeliveryAddress string             `json:"delivery_address" example:"123 Main St, City"`
}

type OrderItemRequest struct {
	MenuItemID   string  `json:"menu_item_id" example:"770e8400-e29b-41d4-a716-446655440000"`
	MenuItemName string  `json:"menu_item_name" example:"Margherita Pizza"`
	Quantity     int     `json:"quantity" example:"2"`
	UnitPrice    float64 `json:"unit_price" example:"12.99"`
}

type UpdateStatusRequest struct {
	Status string `json:"status" example:"CONFIRMED"`
}

type OrderResponse struct {
	ID              string              `json:"id"`
	CustomerID      string              `json:"customer_id"`
	RestaurantID    string              `json:"restaurant_id"`
	DeliveryAddress string              `json:"delivery_address"`
	Status          string              `json:"status"`
	TotalAmount     float64             `json:"total_amount"`
	CreatedAt       time.Time           `json:"created_at"`
	UpdatedAt       time.Time           `json:"updated_at"`
	Items           []OrderItemResponse `json:"items"`
}

type OrderItemResponse struct {
	ID           string  `json:"id"`
	OrderID      string  `json:"order_id"`
	MenuItemID   string  `json:"menu_item_id"`
	MenuItemName string  `json:"menu_item_name"`
	Quantity     int     `json:"quantity"`
	UnitPrice    float64 `json:"unit_price"`
}

type ListOrdersParams struct {
	CustomerID string `json:"customer_id"`
	Status     string `json:"status"`
	StartDate  string `json:"start_date"`
	EndDate    string `json:"end_date"`
}

type ErrorResponse struct {
	Message string `json:"message" example:"something went wrong"`
	Status  int    `json:"status" example:"400"`
}
