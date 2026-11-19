package com.quickbite.delivery.service;

import com.quickbite.delivery.domain.CancelledOrderRepository;
import com.quickbite.delivery.domain.Delivery;
import com.quickbite.delivery.domain.DeliveryRepository;
import com.quickbite.delivery.domain.DeliveryStatus;
import com.quickbite.delivery.domain.Driver;
import com.quickbite.delivery.domain.DriverRepository;
import com.quickbite.security.CurrentUser;
import com.quickbite.security.KeycloakJwtConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Deliveries over HTTP: only the assigned driver's account (or an admin) may see or move them. */
class DeliveryAuthorizationTest {

    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final DeliveryService service = new DeliveryService(deliveries, mock(DriverRepository.class),
            mock(CancelledOrderRepository.class), mock(ApplicationEventPublisher.class), new CurrentUser());

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theAssignedDriverMovesTheDelivery() {
        Delivery delivery = delivery("driver-1");
        signIn("driver-1", "driver");

        assertThat(service.updateStatus(delivery.getId(), "PICKED_UP").status()).isEqualTo("PICKED_UP");
    }

    @Test
    void otherDriversAndCustomersCannotSeeOrMoveIt() {
        Delivery delivery = delivery("driver-1");

        signIn("driver-2", "driver");
        assertThatThrownBy(() -> service.get(delivery.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.updateStatus(delivery.getId(), "DELIVERED"))
                .isInstanceOf(AccessDeniedException.class);

        signIn("customer-1", "customer");
        assertThatThrownBy(() -> service.getByOrderId(delivery.getOrderId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.list(null, null)).isInstanceOf(AccessDeniedException.class);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
    }

    @Test
    void adminsSeeAnyDelivery() {
        Delivery delivery = delivery("driver-1");
        signIn("admin-1", "admin");
        assertThat(service.getByOrderId(delivery.getOrderId()).id()).isEqualTo(delivery.getId());
    }

    private Delivery delivery(String driverUserId) {
        Driver driver = new Driver();
        driver.setId(UUID.randomUUID());
        driver.setUserId(driverUserId);
        driver.setAvailable(false);
        Delivery delivery = new Delivery();
        delivery.setId(UUID.randomUUID());
        delivery.setOrderId(UUID.randomUUID());
        delivery.setDriver(driver);
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        when(deliveries.findWithDriverById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(deliveries.findByOrderId(delivery.getOrderId())).thenReturn(Optional.of(delivery));
        when(deliveries.saveAndFlush(delivery)).thenReturn(delivery);
        return delivery;
    }

    private static void signIn(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(subject)
                .claim("realm_access", Map.of("roles", List.of(roles))).build();
        SecurityContextHolder.getContext().setAuthentication(new KeycloakJwtConverter().convert(jwt));
    }
}
