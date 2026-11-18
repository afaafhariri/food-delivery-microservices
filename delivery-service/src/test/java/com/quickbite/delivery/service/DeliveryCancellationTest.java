package com.quickbite.delivery.service;

import com.quickbite.delivery.domain.CancelledOrder;
import com.quickbite.delivery.domain.CancelledOrderRepository;
import com.quickbite.delivery.domain.Delivery;
import com.quickbite.delivery.domain.DeliveryRepository;
import com.quickbite.delivery.domain.DeliveryStatus;
import com.quickbite.delivery.domain.Driver;
import com.quickbite.delivery.domain.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code order.cancelled} handling in {@link DeliveryService}. */
class DeliveryCancellationTest {

    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final DriverRepository drivers = mock(DriverRepository.class);
    private final CancelledOrderRepository cancelledOrders = mock(CancelledOrderRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DeliveryService service = new DeliveryService(deliveries, drivers, cancelledOrders, events);

    private final UUID orderId = UUID.randomUUID();

    @Test
    void cancelsAnAssignedDeliveryAndFreesTheDriver() {
        Driver driver = driver(false);
        Delivery delivery = delivery(DeliveryStatus.ASSIGNED, driver);
        when(deliveries.findByOrderId(orderId)).thenReturn(Optional.of(delivery));

        service.cancelForOrder(orderId);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(driver.isAvailable()).isTrue();
        verify(events).publishEvent(new DeliveryStatusChanged(delivery.getId(), orderId, driver.getId(), "CANCELLED"));
        verify(cancelledOrders).save(any(CancelledOrder.class));
    }

    @Test
    void leavesADeliveryThatIsAlreadyOnItsWay() {
        Driver driver = driver(false);
        Delivery delivery = delivery(DeliveryStatus.PICKED_UP, driver);
        when(deliveries.findByOrderId(orderId)).thenReturn(Optional.of(delivery));

        service.cancelForOrder(orderId);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(driver.isAvailable()).isFalse();
        verify(events, never()).publishEvent(any());
    }

    @Test
    void cancellationBeforeOrderPlacedPreventsTheAssignment() {
        when(deliveries.findByOrderId(orderId)).thenReturn(Optional.empty());
        service.cancelForOrder(orderId);
        verify(cancelledOrders).save(any(CancelledOrder.class));

        // order.placed arrives late (separate topic, no ordering guarantee).
        when(cancelledOrders.existsById(orderId)).thenReturn(true);
        assertThat(service.assign(orderId)).isEmpty();
        verify(drivers, never()).lockNextAvailable();
        verify(events, never()).publishEvent(any());
    }

    @Test
    void repeatedCancellationIsHarmless() {
        Delivery delivery = delivery(DeliveryStatus.CANCELLED, driver(true));
        when(deliveries.findByOrderId(orderId)).thenReturn(Optional.of(delivery));
        when(cancelledOrders.existsById(orderId)).thenReturn(true);

        service.cancelForOrder(orderId);

        verify(cancelledOrders, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    private static Driver driver(boolean available) {
        Driver driver = new Driver();
        driver.setId(UUID.randomUUID());
        driver.setAvailable(available);
        return driver;
    }

    private Delivery delivery(DeliveryStatus status, Driver driver) {
        Delivery delivery = new Delivery();
        delivery.setId(UUID.randomUUID());
        delivery.setOrderId(orderId);
        delivery.setStatus(status);
        delivery.setDriver(driver);
        return delivery;
    }
}
