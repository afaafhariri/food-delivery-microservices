package com.quickbite.order.service;

import com.quickbite.grpc.restaurant.v1.PricedLine;
import com.quickbite.order.domain.Order;
import com.quickbite.order.domain.OrderItem;
import com.quickbite.order.domain.OrderRepository;
import com.quickbite.order.domain.OrderStatus;
import com.quickbite.order.dto.OrderDtos.CreateOrderRequest;
import com.quickbite.order.dto.OrderDtos.OrderItemResponse;
import com.quickbite.order.dto.OrderDtos.OrderPage;
import com.quickbite.order.dto.OrderDtos.OrderResponse;
import com.quickbite.order.grpc.OrderValidationClient;
import com.quickbite.order.grpc.OrderValidationClient.ValidatedOrder;
import com.quickbite.order.web.ApiException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository repository;
    private final OrderValidationClient validation;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    /**
     * Remote validation runs before the transaction starts, so no DB connection is held
     * while waiting on the network.
     */
    public OrderResponse create(CreateOrderRequest request) {
        boolean hasFreeTextAddress = request.deliveryAddress() != null && !request.deliveryAddress().isBlank();
        if (!hasFreeTextAddress && request.addressId() == null) {
            throw ApiException.badRequest("delivery_address or address_id is required");
        }

        ValidatedOrder validated = validation.validate(request);
        String deliveryAddress = hasFreeTextAddress
                ? request.deliveryAddress().strip()
                : validated.customer().getDeliveryAddress();

        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setRestaurantId(request.restaurantId());
        order.setDeliveryAddress(deliveryAddress);
        order.setStatus(OrderStatus.PLACED);
        order.setTotalAmount(fromMinor(validated.quote().getTotalMinor()));
        for (PricedLine line : validated.quote().getItemsList()) {
            OrderItem item = new OrderItem();
            item.setMenuItemId(UUID.fromString(line.getMenuItemId()));
            item.setMenuItemName(line.getName());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(fromMinor(line.getUnitPriceMinor()));
            order.addItem(item);
        }

        Order saved = tx.execute(status -> {
            Order persisted = repository.save(order);
            events.publishEvent(new OrderPlaced(persisted));
            return persisted;
        });
        log.info("order placed id={} total={} items={}", saved.getId(), saved.getTotalAmount(), saved.getItems().size());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return repository.findWithItemsById(id)
                .map(OrderService::toResponse)
                .orElseThrow(() -> ApiException.notFound("order not found"));
    }

    @Transactional(readOnly = true)
    public OrderPage list(OrderQuery query, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size < 1 || size > MAX_PAGE_SIZE ? DEFAULT_PAGE_SIZE : size;

        Specification<Order> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>(4);
            if (query.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), query.customerId()));
            }
            if (query.status() != null && !query.status().isBlank()) {
                OrderStatus status = OrderStatus.parse(query.status())
                        .orElseThrow(() -> ApiException.badRequest("invalid status: " + query.status()));
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (query.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        query.startDate().atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (query.endDate() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        query.endDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        // Items are loaded with one batched IN query per page (hibernate.default_batch_fetch_size).
        Page<Order> result = repository.findAll(spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new OrderPage(result.map(OrderService::toResponse).getContent(),
                result.getTotalElements(), safePage, safeSize);
    }

    @Transactional
    public OrderResponse updateStatus(UUID id, String rawStatus) {
        OrderStatus status = OrderStatus.parse(rawStatus)
                .orElseThrow(() -> ApiException.badRequest("invalid status: " + rawStatus));
        Order order = load(id);
        order.setStatus(status);
        return toResponse(repository.saveAndFlush(order));
    }

    @Transactional
    public OrderResponse cancel(UUID id) {
        Order order = load(id);
        if (order.getStatus() != OrderStatus.PLACED) {
            throw ApiException.badRequest(
                    "order can only be cancelled when status is PLACED, current status: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        return toResponse(repository.saveAndFlush(order));
    }

    private Order load(UUID id) {
        return repository.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("order not found"));
    }

    private static BigDecimal fromMinor(long minor) {
        return BigDecimal.valueOf(minor, 2);
    }

    static OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getId(), order.getId(), i.getMenuItemId(),
                        i.getMenuItemName(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getRestaurantId(),
                order.getDeliveryAddress(), order.getStatus().name(), order.getTotalAmount(),
                order.getCreatedAt(), order.getUpdatedAt(), items);
    }
}
