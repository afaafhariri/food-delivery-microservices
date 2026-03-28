package handler

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/quickbite/order-service/internal/dto"
	"github.com/quickbite/order-service/internal/service"
)

type OrderHandler struct {
	service *service.OrderService
}

func NewOrderHandler(svc *service.OrderService) *OrderHandler {
	return &OrderHandler{service: svc}
}

func (h *OrderHandler) RegisterRoutes(r chi.Router) {
	r.Route("/api/orders", func(r chi.Router) {
		r.Post("/", h.CreateOrder)
		r.Get("/", h.ListOrders)
		r.Get("/{id}", h.GetOrder)
		r.Patch("/{id}/status", h.UpdateStatus)
		r.Delete("/{id}", h.CancelOrder)
	})
}

// CreateOrder godoc
// @Summary      Create a new order
// @Description  Create a new order with items
// @Tags         orders
// @Accept       json
// @Produce      json
// @Param        request body dto.CreateOrderRequest true "Order creation request"
// @Success      201 {object} dto.OrderResponse
// @Failure      400 {object} dto.ErrorResponse
// @Failure      500 {object} dto.ErrorResponse
// @Router       /api/orders [post]
func (h *OrderHandler) CreateOrder(w http.ResponseWriter, r *http.Request) {
	var req dto.CreateOrderRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	order, err := h.service.CreateOrder(req)
	if err != nil {
		slog.Error("failed to create order", "error", err)
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	writeJSON(w, http.StatusCreated, order)
}

// ListOrders godoc
// @Summary      List orders
// @Description  List orders with optional filters and pagination
// @Tags         orders
// @Produce      json
// @Param        customer_id query string false "Customer ID filter"
// @Param        status query string false "Status filter"
// @Param        start_date query string false "Start date filter (YYYY-MM-DD)"
// @Param        end_date query string false "End date filter (YYYY-MM-DD)"
// @Param        page query int false "Page number" default(1)
// @Param        size query int false "Page size" default(20)
// @Success      200 {object} map[string]interface{}
// @Failure      500 {object} dto.ErrorResponse
// @Router       /api/orders [get]
func (h *OrderHandler) ListOrders(w http.ResponseWriter, r *http.Request) {
	params := dto.ListOrdersParams{
		CustomerID: r.URL.Query().Get("customer_id"),
		Status:     r.URL.Query().Get("status"),
		StartDate:  r.URL.Query().Get("start_date"),
		EndDate:    r.URL.Query().Get("end_date"),
	}

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	if page < 1 {
		page = 1
	}
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size < 1 {
		size = 20
	}

	orders, total, err := h.service.ListOrders(params, page, size)
	if err != nil {
		slog.Error("failed to list orders", "error", err)
		writeError(w, http.StatusInternalServerError, "failed to list orders")
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"data":  orders,
		"total": total,
		"page":  page,
		"size":  size,
	})
}

// GetOrder godoc
// @Summary      Get order by ID
// @Description  Get a specific order by its ID
// @Tags         orders
// @Produce      json
// @Param        id path string true "Order ID"
// @Success      200 {object} dto.OrderResponse
// @Failure      404 {object} dto.ErrorResponse
// @Router       /api/orders/{id} [get]
func (h *OrderHandler) GetOrder(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")

	order, err := h.service.GetOrder(id)
	if err != nil {
		slog.Error("failed to get order", "error", err, "id", id)
		writeError(w, http.StatusNotFound, "order not found")
		return
	}

	writeJSON(w, http.StatusOK, order)
}

// UpdateStatus godoc
// @Summary      Update order status
// @Description  Update the status of an existing order
// @Tags         orders
// @Accept       json
// @Produce      json
// @Param        id path string true "Order ID"
// @Param        request body dto.UpdateStatusRequest true "Status update request"
// @Success      200 {object} dto.OrderResponse
// @Failure      400 {object} dto.ErrorResponse
// @Failure      404 {object} dto.ErrorResponse
// @Router       /api/orders/{id}/status [patch]
func (h *OrderHandler) UpdateStatus(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")

	var req dto.UpdateStatusRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	order, err := h.service.UpdateStatus(id, req)
	if err != nil {
		slog.Error("failed to update order status", "error", err, "id", id)
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, order)
}

// CancelOrder godoc
// @Summary      Cancel an order
// @Description  Cancel an order (only if status is PLACED)
// @Tags         orders
// @Produce      json
// @Param        id path string true "Order ID"
// @Success      200 {object} dto.OrderResponse
// @Failure      400 {object} dto.ErrorResponse
// @Failure      404 {object} dto.ErrorResponse
// @Router       /api/orders/{id} [delete]
func (h *OrderHandler) CancelOrder(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")

	order, err := h.service.CancelOrder(id)
	if err != nil {
		slog.Error("failed to cancel order", "error", err, "id", id)
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, order)
}

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(data); err != nil {
		slog.Error("failed to encode response", "error", err)
	}
}

func writeError(w http.ResponseWriter, status int, message string) {
	w.WriteHeader(status)
	resp := dto.ErrorResponse{
		Message: message,
		Status:  status,
	}
	if err := json.NewEncoder(w).Encode(resp); err != nil {
		slog.Error("failed to encode error response", "error", err)
	}
}
