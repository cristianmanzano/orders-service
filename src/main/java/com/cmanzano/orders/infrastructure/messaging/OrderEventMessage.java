package com.cmanzano.orders.infrastructure.messaging;

import com.cmanzano.orders.domain.event.DomainEvent;
import com.cmanzano.orders.domain.event.OrderCancelled;
import com.cmanzano.orders.domain.event.OrderCreated;
import com.cmanzano.orders.domain.event.OrderPaid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Contrato de integración publicado en Kafka. Es independiente de los eventos de dominio para
 * que el modelo interno pueda cambiar sin romper a los consumidores.
 */
public record OrderEventMessage(
        UUID eventId,
        String eventType,
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        String detail,
        Instant occurredAt) {

    /**
     * Pattern matching sobre una jerarquía sealed: si se agrega un nuevo evento de dominio,
     * este switch deja de compilar hasta que se contemple (no hace falta rama default).
     */
    public static OrderEventMessage from(DomainEvent event) {
        return switch (event) {
            case OrderCreated e -> new OrderEventMessage(e.eventId(), "ORDER_CREATED", e.orderId().toString(),
                    e.customerId().value(), e.total().amount(), e.total().currency(),
                    "Order created with %d item(s), total %s".formatted(e.itemCount(), e.total()), e.occurredAt());
            case OrderPaid e -> new OrderEventMessage(e.eventId(), "ORDER_PAID", e.orderId().toString(),
                    e.customerId().value(), e.total().amount(), e.total().currency(),
                    "Order paid, reference " + e.paymentReference(), e.occurredAt());
            case OrderCancelled e -> new OrderEventMessage(e.eventId(), "ORDER_CANCELLED", e.orderId().toString(),
                    e.customerId().value(), null, null, "Order cancelled: " + e.reason(), e.occurredAt());
        };
    }
}
