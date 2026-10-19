package com.cmanzano.orders.domain.event;

import com.cmanzano.orders.domain.model.CustomerId;
import com.cmanzano.orders.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelled(UUID eventId, OrderId orderId, CustomerId customerId, String reason,
                             Instant occurredAt) implements DomainEvent {
}
