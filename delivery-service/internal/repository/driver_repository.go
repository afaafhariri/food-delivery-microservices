package repository

import (
	"github.com/quickbite/delivery-service/internal/model"
	"gorm.io/gorm"
)

type DriverRepository struct {
	db *gorm.DB
}

func NewDriverRepository(db *gorm.DB) *DriverRepository {
	return &DriverRepository{db: db}
}

func (r *DriverRepository) Create(driver *model.Driver) error {
	return r.db.Create(driver).Error
}

func (r *DriverRepository) FindByID(id string) (*model.Driver, error) {
	var driver model.Driver
	if err := r.db.First(&driver, "id = ?", id).Error; err != nil {
		return nil, err
	}
	return &driver, nil
}

func (r *DriverRepository) FindAll(available *bool) ([]model.Driver, error) {
	var drivers []model.Driver
	query := r.db
	if available != nil {
		query = query.Where("available = ?", *available)
	}
	if err := query.Order("created_at ASC").Find(&drivers).Error; err != nil {
		return nil, err
	}
	return drivers, nil
}

func (r *DriverRepository) Update(driver *model.Driver) error {
	return r.db.Save(driver).Error
}

func (r *DriverRepository) FindNextAvailable() (*model.Driver, error) {
	var driver model.Driver
	if err := r.db.Where("available = ?", true).Order("created_at ASC").First(&driver).Error; err != nil {
		return nil, err
	}
	return &driver, nil
}
