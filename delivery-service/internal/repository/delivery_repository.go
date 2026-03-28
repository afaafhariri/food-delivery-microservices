package repository

import (
	"github.com/quickbite/delivery-service/internal/model"
	"gorm.io/gorm"
)

type DeliveryRepository struct {
	db *gorm.DB
}

func NewDeliveryRepository(db *gorm.DB) *DeliveryRepository {
	return &DeliveryRepository{db: db}
}

func (r *DeliveryRepository) Create(delivery *model.Delivery) error {
	return r.db.Create(delivery).Error
}

func (r *DeliveryRepository) FindByID(id string) (*model.Delivery, error) {
	var delivery model.Delivery
	if err := r.db.Preload("Driver").First(&delivery, "id = ?", id).Error; err != nil {
		return nil, err
	}
	return &delivery, nil
}

func (r *DeliveryRepository) FindAll(driverID, status string) ([]model.Delivery, error) {
	var deliveries []model.Delivery
	query := r.db.Preload("Driver")
	if driverID != "" {
		query = query.Where("driver_id = ?", driverID)
	}
	if status != "" {
		query = query.Where("status = ?", status)
	}
	if err := query.Order("created_at DESC").Find(&deliveries).Error; err != nil {
		return nil, err
	}
	return deliveries, nil
}

func (r *DeliveryRepository) FindByOrderID(orderID string) (*model.Delivery, error) {
	var delivery model.Delivery
	if err := r.db.Preload("Driver").First(&delivery, "order_id = ?", orderID).Error; err != nil {
		return nil, err
	}
	return &delivery, nil
}

func (r *DeliveryRepository) Update(delivery *model.Delivery) error {
	return r.db.Save(delivery).Error
}
