package com.cmanzano.orders.domain.event;

import com.cmanzano.orders.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

/**
 * Jerarquía cerrada (sealed) de eventos de dominio: el compilador garantiza que un
 * switch sobre DomainEvent cubre todos los casos.
 */
public sealed interface DomainEvent permits OrderCreated, OrderPaid, OrderCancelled {

    UUID eventId();

    OrderId orderId();

    Instant occurredAt();
}
