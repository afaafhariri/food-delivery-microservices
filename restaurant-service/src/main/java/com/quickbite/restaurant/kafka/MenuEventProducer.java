package com.quickbite.restaurant.kafka;

import com.quickbite.messaging.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * {@code restaurant.menu.updated}, written to the transactional outbox. Callers are
 * {@code @Transactional} service methods, so an event exists only if the menu change commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuEventProducer {

    private static final String TOPIC = "restaurant.menu.updated";

    private final Outbox outbox;

    public void publishMenuEvent(UUID restaurantId, UUID itemId, MenuAction action) {
        outbox.publish(TOPIC, restaurantId.toString(), Map.of(
                "restaurantId", restaurantId.toString(),
                "itemId", itemId.toString(),
                "action", action.name()));
        log.info("Queued menu event: restaurantId={}, itemId={}, action={}", restaurantId, itemId, action);
    }

    public enum MenuAction {
        CREATED, UPDATED, DELETED
    }
}
