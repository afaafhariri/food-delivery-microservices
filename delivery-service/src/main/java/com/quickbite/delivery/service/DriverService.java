package com.quickbite.delivery.service;

import com.quickbite.delivery.domain.Driver;
import com.quickbite.delivery.domain.DriverRepository;
import com.quickbite.delivery.dto.DeliveryDtos.CreateDriverRequest;
import com.quickbite.delivery.dto.DeliveryDtos.DriverResponse;
import com.quickbite.delivery.dto.DeliveryDtos.UpdateDriverRequest;
import com.quickbite.delivery.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository drivers;

    @Transactional
    public DriverResponse register(CreateDriverRequest request) {
        Driver driver = new Driver();
        driver.setName(request.name().strip());
        driver.setPhone(request.phone().strip());
        driver.setVehicleType(request.vehicleType().strip());
        driver.setAvailable(true);
        return toResponse(drivers.saveAndFlush(driver));
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> list(Boolean available) {
        List<Driver> result = available == null
                ? drivers.findAllByOrderByCreatedAtAsc()
                : drivers.findByAvailableOrderByCreatedAtAsc(available);
        return result.stream().map(DriverService::toResponse).toList();
    }

    @Transactional
    public DriverResponse update(UUID id, UpdateDriverRequest request) {
        Driver driver = drivers.findById(id).orElseThrow(() -> ApiException.notFound("driver not found"));
        if (hasText(request.name())) {
            driver.setName(request.name().strip());
        }
        if (hasText(request.phone())) {
            driver.setPhone(request.phone().strip());
        }
        if (hasText(request.vehicleType())) {
            driver.setVehicleType(request.vehicleType().strip());
        }
        if (request.available() != null) {
            driver.setAvailable(request.available());
        }
        return toResponse(drivers.saveAndFlush(driver));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static DriverResponse toResponse(Driver d) {
        return new DriverResponse(d.getId(), d.getName(), d.getPhone(), d.getVehicleType(),
                d.isAvailable(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
