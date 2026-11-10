package com.quickbite.delivery.web;

import com.quickbite.delivery.dto.DeliveryDtos.DeliveryResponse;
import com.quickbite.delivery.dto.DeliveryDtos.UpdateDeliveryStatusRequest;
import com.quickbite.delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
@Tag(name = "deliveries")
public class DeliveryController {

    private final DeliveryService deliveries;

    @GetMapping
    @Operation(summary = "List deliveries with optional driver / status filters")
    public List<DeliveryResponse> list(
            @RequestParam(name = "driver_id", required = false) UUID driverId,
            @RequestParam(required = false) String status) {
        return deliveries.list(driverId, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a delivery by ID")
    public DeliveryResponse get(@PathVariable UUID id) {
        return deliveries.get(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update delivery status")
    public DeliveryResponse updateStatus(@PathVariable UUID id, @RequestBody UpdateDeliveryStatusRequest request) {
        return deliveries.updateStatus(id, request.status());
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get the delivery for an order")
    public DeliveryResponse byOrder(@PathVariable UUID orderId) {
        return deliveries.getByOrderId(orderId);
    }
}
