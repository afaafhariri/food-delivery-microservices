package com.quickbite.restaurant.grpc;

import com.quickbite.grpc.restaurant.v1.QuoteLine;
import com.quickbite.grpc.restaurant.v1.QuoteOrderRequest;
import com.quickbite.grpc.restaurant.v1.QuoteOrderResponse;
import com.quickbite.restaurant.model.MenuItem;
import com.quickbite.restaurant.model.Restaurant;
import com.quickbite.restaurant.repository.MenuItemRepository;
import com.quickbite.restaurant.repository.RestaurantRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderQuoteServiceTest {

    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final MenuItemRepository menuItems = mock(MenuItemRepository.class);
    private final OrderQuoteService service = new OrderQuoteService(restaurants, menuItems);

    private final UUID restaurantId = UUID.randomUUID();
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().id(restaurantId).name("Spice Garden").active(true).build();
        when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    }

    @Test
    void pricesOrderInMinorUnitsFromTheMenu() {
        MenuItem kottu = item("Kottu", "750.00", true);
        MenuItem dessert = item("Watalappam", "350.50", true);
        when(menuItems.findByRestaurantIdAndIdIn(eq(restaurantId), any())).thenReturn(List.of(kottu, dessert));

        QuoteOrderResponse quote = service.quote(request(line(kottu, 2), line(dessert, 1)));

        assertThat(quote.getRestaurantName()).isEqualTo("Spice Garden");
        assertThat(quote.getItemsList()).extracting(l -> l.getName()).containsExactly("Kottu", "Watalappam");
        assertThat(quote.getItems(0).getUnitPriceMinor()).isEqualTo(75_000);
        assertThat(quote.getItems(0).getLineTotalMinor()).isEqualTo(150_000);
        assertThat(quote.getTotalMinor()).isEqualTo(185_050);
    }

    @Test
    void rejectsInactiveRestaurant() {
        restaurant.setActive(false);
        assertCode(() -> service.quote(request(QuoteLine.newBuilder()
                .setMenuItemId(UUID.randomUUID().toString()).setQuantity(1).build())), Status.Code.NOT_FOUND);
    }

    @Test
    void rejectsItemFromAnotherRestaurant() {
        when(menuItems.findByRestaurantIdAndIdIn(eq(restaurantId), any())).thenReturn(List.of());
        assertCode(() -> service.quote(request(QuoteLine.newBuilder()
                .setMenuItemId(UUID.randomUUID().toString()).setQuantity(1).build())), Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void rejectsUnavailableItem() {
        MenuItem soldOut = item("Hoppers", "500.00", false);
        when(menuItems.findByRestaurantIdAndIdIn(eq(restaurantId), any())).thenReturn(List.of(soldOut));
        assertCode(() -> service.quote(request(line(soldOut, 1))), Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void rejectsBadInput() {
        assertCode(() -> service.quote(QuoteOrderRequest.newBuilder().setRestaurantId("nope").build()),
                Status.Code.INVALID_ARGUMENT);
        assertCode(() -> service.quote(QuoteOrderRequest.newBuilder().setRestaurantId(restaurantId.toString()).build()),
                Status.Code.INVALID_ARGUMENT);
        MenuItem kottu = item("Kottu", "750.00", true);
        assertCode(() -> service.quote(request(line(kottu, 0))), Status.Code.INVALID_ARGUMENT);
    }

    private MenuItem item(String name, String price, boolean available) {
        return MenuItem.builder().id(UUID.randomUUID()).restaurant(restaurant).name(name)
                .price(new BigDecimal(price)).available(available).build();
    }

    private static QuoteLine line(MenuItem item, int quantity) {
        return QuoteLine.newBuilder().setMenuItemId(item.getId().toString()).setQuantity(quantity).build();
    }

    private QuoteOrderRequest request(QuoteLine... lines) {
        return QuoteOrderRequest.newBuilder().setRestaurantId(restaurantId.toString())
                .addAllItems(List.of(lines)).build();
    }

    private static void assertCode(Runnable call, Status.Code code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode()).isEqualTo(code));
    }
}
