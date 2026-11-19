package com.quickbite.order.service;

import com.quickbite.grpc.customer.v1.ResolveCustomerResponse;
import com.quickbite.grpc.restaurant.v1.QuoteOrderResponse;
import com.quickbite.order.domain.Order;
import com.quickbite.order.domain.OrderRepository;
import com.quickbite.order.domain.OrderStatus;
import com.quickbite.order.dto.OrderDtos.CreateOrderRequest;
import com.quickbite.order.dto.OrderDtos.OrderItemRequest;
import com.quickbite.order.grpc.OrderValidationClient;
import com.quickbite.order.grpc.OrderValidationClient.ValidatedOrder;
import com.quickbite.security.CurrentUser;
import com.quickbite.security.KeycloakJwtConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Who may place, read, cancel and update orders. */
class OrderAuthorizationTest {

    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderValidationClient validation = mock(OrderValidationClient.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final OrderService service = new OrderService(repository, validation,
            mock(ApplicationEventPublisher.class), tx, new CurrentUser());

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customersCannotOrderForSomeoneElsesProfile() {
        signIn("customer-1", "customer");
        when(validation.validate(any())).thenReturn(new ValidatedOrder(QuoteOrderResponse.getDefaultInstance(),
                ResolveCustomerResponse.newBuilder().setUserId("customer-2").build()));

        assertThatThrownBy(() -> service.create(request())).isInstanceOf(AccessDeniedException.class);
        verify(tx, never()).execute(any());
    }

    @Test
    void driversAndRestaurantsCannotPlaceOrders() {
        signIn("driver-1", "driver");
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(AccessDeniedException.class);
        verify(validation, never()).validate(any());
    }

    @Test
    void customersReadAndCancelOnlyTheirOwnOrders() {
        Order mine = order("customer-1");
        Order theirs = order("customer-2");

        signIn("customer-1", "customer");
        assertThat(service.get(mine.getId()).id()).isEqualTo(mine.getId());
        assertThatThrownBy(() -> service.get(theirs.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.cancel(theirs.getId())).isInstanceOf(AccessDeniedException.class);
        assertThat(theirs.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(service.cancel(mine.getId()).status()).isEqualTo("CANCELLED");
    }

    @Test
    void onlyAdminsSetTheStatusDirectly() {
        Order mine = order("customer-1");

        signIn("customer-1", "customer");
        assertThatThrownBy(() -> service.updateStatus(mine.getId(), "DELIVERED"))
                .isInstanceOf(AccessDeniedException.class);

        signIn("admin-1", "admin");
        assertThat(service.updateStatus(mine.getId(), "CONFIRMED").status()).isEqualTo("CONFIRMED");
    }

    @Test
    void adminsReadEveryOrder() {
        Order theirs = order("customer-2");
        signIn("admin-1", "admin");
        assertThat(service.get(theirs.getId()).id()).isEqualTo(theirs.getId());
    }

    private static CreateOrderRequest request() {
        return new CreateOrderRequest(UUID.randomUUID(), UUID.randomUUID(), null, "1 Test St",
                List.of(new OrderItemRequest(UUID.randomUUID(), 1)));
    }

    private Order order(String customerUserId) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID());
        order.setCustomerUserId(customerUserId);
        order.setDeliveryAddress("1 Test St");
        order.setTotalAmount(BigDecimal.TEN);
        order.setStatus(OrderStatus.PLACED);
        when(repository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));
        when(repository.saveAndFlush(order)).thenReturn(order);
        return order;
    }

    private static void signIn(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(subject)
                .claim("realm_access", Map.of("roles", List.of(roles))).build();
        SecurityContextHolder.getContext().setAuthentication(new KeycloakJwtConverter().convert(jwt));
    }
}
