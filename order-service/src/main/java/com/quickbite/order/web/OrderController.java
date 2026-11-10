package com.quickbite.order.web;

import com.quickbite.order.dto.OrderDtos.CreateOrderRequest;
import com.quickbite.order.dto.OrderDtos.OrderPage;
import com.quickbite.order.dto.OrderDtos.OrderResponse;
import com.quickbite.order.dto.OrderDtos.UpdateStatusRequest;
import com.quickbite.order.service.OrderQuery;
import com.quickbite.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "orders")
public class OrderController {

    private final OrderService orders;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place an order (validated and priced via gRPC)")
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orders.create(request);
    }

    @GetMapping
    @Operation(summary = "List orders with optional filters and pagination")
    public OrderPage list(
            @RequestParam(name = "customer_id", required = false) UUID customerId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orders.list(new OrderQuery(customerId, status, startDate, endDate), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    public OrderResponse get(@PathVariable UUID id) {
        return orders.get(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status")
    public OrderResponse updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest request) {
        return orders.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an order (only while PLACED)")
    public OrderResponse cancel(@PathVariable UUID id) {
        return orders.cancel(id);
    }
}
