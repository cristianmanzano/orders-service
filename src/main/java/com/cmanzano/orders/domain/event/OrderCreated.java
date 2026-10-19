package com.cmanzano.orders.domain.event;

import com.cmanzano.orders.domain.model.CustomerId;
import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

public record OrderCreated(UUID eventId, OrderId orderId, CustomerId customerId, Money total, int itemCount,
                           Instant occurredAt) implements DomainEvent {
}
