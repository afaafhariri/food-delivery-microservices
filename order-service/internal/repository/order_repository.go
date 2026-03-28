package repository

import (
	"time"

	"github.com/quickbite/order-service/internal/dto"
	"github.com/quickbite/order-service/internal/model"
	"gorm.io/gorm"
)

type OrderRepository struct {
	db *gorm.DB
}

func NewOrderRepository(db *gorm.DB) *OrderRepository {
	return &OrderRepository{db: db}
}

func (r *OrderRepository) Create(order *model.Order) error {
	return r.db.Create(order).Error
}

func (r *OrderRepository) FindByID(id string) (*model.Order, error) {
	var order model.Order
	err := r.db.Preload("Items").First(&order, "id = ?", id).Error
	if err != nil {
		return nil, err
	}
	return &order, nil
}

func (r *OrderRepository) FindAll(params dto.ListOrdersParams, page, size int) ([]model.Order, int64, error) {
	var orders []model.Order
	var total int64

	query := r.db.Model(&model.Order{})

	if params.CustomerID != "" {
		query = query.Where("customer_id = ?", params.CustomerID)
	}
	if params.Status != "" {
		query = query.Where("status = ?", params.Status)
	}
	if params.StartDate != "" {
		if t, err := time.Parse("2006-01-02", params.StartDate); err == nil {
			query = query.Where("created_at >= ?", t)
		}
	}
	if params.EndDate != "" {
		if t, err := time.Parse("2006-01-02", params.EndDate); err == nil {
			query = query.Where("created_at <= ?", t.Add(24*time.Hour))
		}
	}

	if err := query.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	offset := (page - 1) * size
	err := query.Preload("Items").
		Order("created_at DESC").
		Offset(offset).
		Limit(size).
		Find(&orders).Error

	return orders, total, err
}

func (r *OrderRepository) Update(order *model.Order) error {
	return r.db.Save(order).Error
}

func (r *OrderRepository) Delete(id string) error {
	return r.db.Delete(&model.Order{}, "id = ?", id).Error
}
