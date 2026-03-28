package handler

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/quickbite/delivery-service/internal/dto"
	"github.com/quickbite/delivery-service/internal/service"
)

type DeliveryHandler struct {
	service *service.DeliveryService
}

func NewDeliveryHandler(service *service.DeliveryService) *DeliveryHandler {
	return &DeliveryHandler{service: service}
}

// ListDeliveries godoc
// @Summary List all deliveries
// @Description List all deliveries with optional filters
// @Tags deliveries
// @Produce json
// @Param driver_id query string false "Filter by driver ID"
// @Param status query string false "Filter by status"
// @Success 200 {array} dto.DeliveryResponse
// @Failure 500 {object} dto.ErrorResponse
// @Router /api/deliveries [get]
func (h *DeliveryHandler) ListDeliveries(w http.ResponseWriter, r *http.Request) {
	driverID := r.URL.Query().Get("driver_id")
	status := r.URL.Query().Get("status")

	resp, err := h.service.ListDeliveries(driverID, status)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, resp)
}

// GetDelivery godoc
// @Summary Get a delivery by ID
// @Description Get delivery details by ID
// @Tags deliveries
// @Produce json
// @Param id path string true "Delivery ID"
// @Success 200 {object} dto.DeliveryResponse
// @Failure 404 {object} dto.ErrorResponse
// @Router /api/deliveries/{id} [get]
func (h *DeliveryHandler) GetDelivery(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")

	resp, err := h.service.GetDelivery(id)
	if err != nil {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, resp)
}

// UpdateDeliveryStatus godoc
// @Summary Update delivery status
// @Description Update the status of a delivery
// @Tags deliveries
// @Accept json
// @Produce json
// @Param id path string true "Delivery ID"
// @Param request body dto.UpdateDeliveryStatusRequest true "Status update data"
// @Success 200 {object} dto.DeliveryResponse
// @Failure 400 {object} dto.ErrorResponse
// @Failure 404 {object} dto.ErrorResponse
// @Router /api/deliveries/{id}/status [patch]
func (h *DeliveryHandler) UpdateDeliveryStatus(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")

	var req dto.UpdateDeliveryStatusRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if req.Status == "" {
		writeError(w, http.StatusBadRequest, "status is required")
		return
	}

	resp, err := h.service.UpdateDeliveryStatus(id, req.Status)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, resp)
}

// GetDeliveryByOrderID godoc
// @Summary Get delivery by order ID
// @Description Get delivery details by order ID
// @Tags deliveries
// @Produce json
// @Param orderId path string true "Order ID"
// @Success 200 {object} dto.DeliveryResponse
// @Failure 404 {object} dto.ErrorResponse
// @Router /api/deliveries/order/{orderId} [get]
func (h *DeliveryHandler) GetDeliveryByOrderID(w http.ResponseWriter, r *http.Request) {
	orderID := chi.URLParam(r, "orderId")

	resp, err := h.service.GetDeliveryByOrderID(orderID)
	if err != nil {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, resp)
}

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, status int, message string) {
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(dto.ErrorResponse{
		Message: message,
		Status:  status,
	})
}
