package com.quickbite.delivery.service;

import com.quickbite.delivery.domain.Driver;
import com.quickbite.delivery.domain.DriverRepository;
import com.quickbite.delivery.dto.DeliveryDtos.CreateDriverRequest;
import com.quickbite.delivery.dto.DeliveryDtos.DriverResponse;
import com.quickbite.delivery.dto.DeliveryDtos.UpdateDriverRequest;
import com.quickbite.delivery.web.ApiException;
import com.quickbite.security.CurrentUser;
import com.quickbite.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Driver profiles. A profile belongs to the Keycloak account that registered it ({@code user_id});
 * each driver account has at most one. Drivers see and change only their own profile; admins all.
 * A driver who is registered or set available may be given a waiting order at once.
 */
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository drivers;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    @Transactional
    public DriverResponse register(CreateDriverRequest request) {
        currentUser.requireAnyRole(Roles.DRIVER);
        // A driver account registers itself; an admin without the driver role creates an
        // admin-managed driver that no account owns.
        String userId = currentUser.hasRole(Roles.DRIVER) ? currentUser.id() : null;
        if (userId != null && drivers.existsByUserId(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "this account is already registered as a driver (GET /api/drivers/me)");
        }
        Driver driver = new Driver();
        driver.setName(request.name().strip());
        driver.setPhone(request.phone().strip());
        driver.setVehicleType(request.vehicleType().strip());
        driver.setUserId(userId);
        driver.setAvailable(true);
        Driver saved = drivers.saveAndFlush(driver);
        events.publishEvent(new RetryWaitingOrders("driver " + saved.getId() + " registered"));
        return toResponse(saved);
    }

    /** The caller's own driver profile. */
    @Transactional(readOnly = true)
    public DriverResponse mine() {
        return drivers.findByUserId(currentUser.id())
                .map(DriverService::toResponse)
                .orElseThrow(() -> ApiException.notFound("this account is not registered as a driver"));
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> list(Boolean available) {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("only admins can list drivers");
        }
        List<Driver> result = available == null
                ? drivers.findAllByOrderByCreatedAtAsc()
                : drivers.findByAvailableOrderByCreatedAtAsc(available);
        return result.stream().map(DriverService::toResponse).toList();
    }

    @Transactional
    public DriverResponse update(UUID id, UpdateDriverRequest request) {
        Driver driver = drivers.findById(id).orElseThrow(() -> ApiException.notFound("driver not found"));
        currentUser.requireOwnerOrAdmin(driver.getUserId());
        if (hasText(request.name())) {
            driver.setName(request.name().strip());
        }
        if (hasText(request.phone())) {
            driver.setPhone(request.phone().strip());
        }
        if (hasText(request.vehicleType())) {
            driver.setVehicleType(request.vehicleType().strip());
        }
        boolean becameAvailable = Boolean.TRUE.equals(request.available()) && !driver.isAvailable();
        if (request.available() != null) {
            driver.setAvailable(request.available());
        }
        Driver saved = drivers.saveAndFlush(driver);
        if (becameAvailable) {
            events.publishEvent(new RetryWaitingOrders("driver " + id + " set available"));
        }
        return toResponse(saved);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static DriverResponse toResponse(Driver d) {
        return new DriverResponse(d.getId(), d.getName(), d.getPhone(), d.getVehicleType(),
                d.isAvailable(), d.getUserId(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
