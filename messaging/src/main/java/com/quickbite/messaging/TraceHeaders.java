package com.quickbite.messaging;

import java.util.Map;

/** Captures the current trace context as W3C headers ({@code traceparent}), stored with an outbox event. */
@FunctionalInterface
public interface TraceHeaders {

    TraceHeaders NONE = Map::of;

    Map<String, String> current();
}
