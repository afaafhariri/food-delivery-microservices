package com.quickbite.delivery.web;

import com.quickbite.delivery.dto.DeliveryDtos.CreateDriverRequest;
import com.quickbite.delivery.dto.DeliveryDtos.DriverResponse;
import com.quickbite.delivery.dto.DeliveryDtos.UpdateDriverRequest;
import com.quickbite.delivery.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "drivers")
public class DriverController {

    private final DriverService drivers;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new driver")
    public DriverResponse register(@Valid @RequestBody CreateDriverRequest request) {
        return drivers.register(request);
    }

    @GetMapping
    @Operation(summary = "List drivers, optionally filtered by availability")
    public List<DriverResponse> list(@RequestParam(required = false) Boolean available) {
        return drivers.list(available);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a driver")
    public DriverResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDriverRequest request) {
        return drivers.update(id, request);
    }
}
