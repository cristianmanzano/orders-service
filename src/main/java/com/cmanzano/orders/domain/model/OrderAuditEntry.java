package com.cmanzano.orders.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro inmutable de la traza de auditoría de un pedido.
 */
public record OrderAuditEntry(OrderId orderId, UUID eventId, String eventType, String detail, Instant occurredAt) {

    public OrderAuditEntry {
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(eventId, "event id is required");
        Objects.requireNonNull(eventType, "event type is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }
}
