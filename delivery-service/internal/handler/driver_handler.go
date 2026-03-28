package handler

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/quickbite/delivery-service/internal/dto"
	"github.com/quickbite/delivery-service/internal/service"
)

type DriverHandler struct {
	service *service.DriverService
}

func NewDriverHandler(service *service.DriverService) *DriverHandler {
	return &DriverHandler{service: service}
}

// RegisterDriver godoc
// @Summary Register a new driver
// @Description Register a new delivery driver
// @Tags drivers
// @Accept json
// @Produce json
// @Param request body dto.CreateDriverRequest true "Driver registration data"
// @Success 201 {object} dto.DriverResponse
// @Failure 400 {object} dto.ErrorResponse
// @Failure 500 {object} dto.ErrorResponse
// @Router /api/drivers [post]
func (h *DriverHandler) RegisterDriver(w http.ResponseWriter, r *http.Request) {
	var req dto.CreateDriverRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	resp, err := h.service.Create(req)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	writeJSON(w, http.StatusCreated, resp)
}

// ListDrivers godoc
// @Summary List all drivers
// @Description List all drivers with optional availability filter
// @Tags drivers
// @Produce json
// @Param available query bool false "Filter by availability"
// @Success 200 {array} dto.DriverResponse
// @Failure 500 {object} dto.ErrorResponse
// @Router /api/drivers [get]
func (h *DriverHandler) ListDrivers(w http.ResponseWriter, r *http.Request) {
	var available *bool
	if q := r.URL.Query().Get("available"); q != "" {
		val, err := strconv.ParseBool(q)
		if err == nil {
			available = &val
		}
	}

	resp, err := h.service.ListDrivers(available)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, resp)
}

// UpdateDriver godoc
// @Summary Update a driver
// @Description Update driver information
// @Tags drivers
// @Accept json
// @Produce json
// @Param id path string true "Driver ID"
// @Param request body dto.UpdateDriverRequest true "Driver update data"
// @Success 200 {object} dto.DriverResponse
// @Failure 400 {object} dto.ErrorResponse
// @Failure 404 {object} dto.ErrorResponse
// @Router /api/drivers/{id} [put]
func (h *DriverHandler) UpdateDriver(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")

	var req dto.UpdateDriverRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	resp, err := h.service.UpdateDriver(id, req)
	if err != nil {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, resp)
}
