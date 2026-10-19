package com.cmanzano.orders.domain.event;

import com.cmanzano.orders.domain.model.CustomerId;
import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

public record OrderPaid(UUID eventId, OrderId orderId, CustomerId customerId, Money total, String paymentReference,
                        Instant occurredAt) implements DomainEvent {
}
