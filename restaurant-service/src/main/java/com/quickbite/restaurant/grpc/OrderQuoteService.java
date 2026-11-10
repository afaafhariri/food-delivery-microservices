package com.quickbite.restaurant.grpc;

import com.quickbite.grpc.restaurant.v1.PricedLine;
import com.quickbite.grpc.restaurant.v1.QuoteLine;
import com.quickbite.grpc.restaurant.v1.QuoteOrderRequest;
import com.quickbite.grpc.restaurant.v1.QuoteOrderResponse;
import com.quickbite.restaurant.model.MenuItem;
import com.quickbite.restaurant.model.Restaurant;
import com.quickbite.restaurant.repository.MenuItemRepository;
import com.quickbite.restaurant.repository.RestaurantRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Server-side pricing: the restaurant's menu is the single source of truth for names and prices. */
@Service
@RequiredArgsConstructor
public class OrderQuoteService {

    static final int MAX_QUANTITY_PER_LINE = 100;

    private final RestaurantRepository restaurants;
    private final MenuItemRepository menuItems;

    @Transactional(readOnly = true)
    public QuoteOrderResponse quote(QuoteOrderRequest request) {
        UUID restaurantId = parseId(request.getRestaurantId(), "restaurant_id");
        if (request.getItemsCount() == 0) {
            throw invalid("at least one item is required");
        }

        Restaurant restaurant = restaurants.findById(restaurantId)
                .filter(Restaurant::isActive)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("restaurant not found: " + restaurantId).asRuntimeException());

        Set<UUID> ids = new HashSet<>();
        for (QuoteLine line : request.getItemsList()) {
            ids.add(parseId(line.getMenuItemId(), "menu_item_id"));
            if (line.getQuantity() < 1 || line.getQuantity() > MAX_QUANTITY_PER_LINE) {
                throw invalid("quantity must be between 1 and " + MAX_QUANTITY_PER_LINE);
            }
        }

        // One query for all requested items, scoped to this restaurant.
        Map<UUID, MenuItem> menu = menuItems.findByRestaurantIdAndIdIn(restaurantId, ids).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

        QuoteOrderResponse.Builder response = QuoteOrderResponse.newBuilder()
                .setRestaurantId(restaurantId.toString())
                .setRestaurantName(restaurant.getName());
        long total = 0;
        for (QuoteLine line : request.getItemsList()) {
            MenuItem item = menu.get(UUID.fromString(line.getMenuItemId()));
            if (item == null) {
                throw Status.FAILED_PRECONDITION.withDescription(
                        "menu item " + line.getMenuItemId() + " is not on the menu of " + restaurant.getName())
                        .asRuntimeException();
            }
            if (!item.isAvailable()) {
                throw Status.FAILED_PRECONDITION.withDescription(
                        "menu item '" + item.getName() + "' is currently unavailable").asRuntimeException();
            }
            long unit = item.getPrice().movePointRight(2).longValueExact();
            long lineTotal = Math.multiplyExact(unit, line.getQuantity());
            total = Math.addExact(total, lineTotal);
            response.addItems(PricedLine.newBuilder()
                    .setMenuItemId(item.getId().toString())
                    .setName(item.getName())
                    .setQuantity(line.getQuantity())
                    .setUnitPriceMinor(unit)
                    .setLineTotalMinor(lineTotal));
        }
        return response.setTotalMinor(total).build();
    }

    private static UUID parseId(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw invalid("invalid " + field + ": '" + value + "'");
        }
    }

    private static StatusRuntimeException invalid(String message) {
        return Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
    }
}
