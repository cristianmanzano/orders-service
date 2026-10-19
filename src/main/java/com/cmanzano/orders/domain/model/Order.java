package com.cmanzano.orders.domain.model;

import com.cmanzano.orders.domain.event.DomainEvent;
import com.cmanzano.orders.domain.event.OrderCancelled;
import com.cmanzano.orders.domain.event.OrderCreated;
import com.cmanzano.orders.domain.event.OrderPaid;
import com.cmanzano.orders.domain.exception.InvalidOrderException;
import com.cmanzano.orders.domain.exception.InvalidOrderStateException;
import com.cmanzano.orders.domain.policy.DiscountPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Raíz del agregado Pedido.
 * <p>
 * Encapsula sus invariantes: el estado solo cambia a través de métodos de negocio
 * ({@link #markAsPaid}, {@link #cancel}) y nunca mediante setters. Cada cambio relevante
 * registra un {@link DomainEvent} que la capa de aplicación publica tras persistir.
 */
public class Order {

    private final OrderId id;
    private final CustomerId customerId;
    private final List<OrderItem> items;
    private final Money subtotal;
    private final Money discount;
    private final Money total;
    private final Instant createdAt;
    private final Long version;
    private OrderStatus status;
    private String paymentReference;
    private String cancellationReason;
    private Instant updatedAt;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Order(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id is required");
        this.customerId = Objects.requireNonNull(builder.customerId, "customerId is required");
        this.items = List.copyOf(Objects.requireNonNull(builder.items, "items are required"));
        this.subtotal = Objects.requireNonNull(builder.subtotal, "subtotal is required");
        this.discount = Objects.requireNonNull(builder.discount, "discount is required");
        this.total = Objects.requireNonNull(builder.total, "total is required");
        this.status = Objects.requireNonNull(builder.status, "status is required");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt is required");
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : builder.createdAt;
        this.paymentReference = builder.paymentReference;
        this.cancellationReason = builder.cancellationReason;
        this.version = builder.version;
    }

    /**
     * Factory Method: crea un pedido nuevo aplicando la política de descuento recibida.
     */
    public static Order create(CustomerId customerId, List<OrderItem> items, DiscountPolicy discountPolicy, Clock clock) {
        if (items == null || items.isEmpty()) {
            throw new InvalidOrderException("An order must contain at least one item");
        }
        String currency = items.getFirst().unitPrice().currency();
        if (items.stream().anyMatch(item -> !item.unitPrice().currency().equals(currency))) {
            throw new InvalidOrderException("All items of an order must share the same currency");
        }

        Money subtotal = items.stream().map(OrderItem::subtotal).reduce(Money.zero(currency), Money::add);
        Money discount = discountPolicy.discountFor(subtotal, items).min(subtotal);
        Instant now = clock.instant();

        Order order = builder()
                .id(OrderId.newId())
                .customerId(customerId)
                .items(items)
                .subtotal(subtotal)
                .discount(discount)
                .total(subtotal.subtract(discount))
                .status(OrderStatus.CREATED)
                .createdAt(now)
                .build();

        order.record(new OrderCreated(UUID.randomUUID(), order.id, customerId, order.total, items.size(), now));
        return order;
    }

    public void ensurePayable() {
        ensureCanTransitionTo(OrderStatus.PAID);
    }

    public void markAsPaid(String paymentReference, Clock clock) {
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new InvalidOrderException("A payment reference is required");
        }
        transitionTo(OrderStatus.PAID, clock);
        this.paymentReference = paymentReference;
        record(new OrderPaid(UUID.randomUUID(), id, customerId, total, paymentReference, updatedAt));
    }

    public void cancel(String reason, Clock clock) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidOrderException("A cancellation reason is required");
        }
        transitionTo(OrderStatus.CANCELLED, clock);
        this.cancellationReason = reason;
        record(new OrderCancelled(UUID.randomUUID(), id, customerId, reason, updatedAt));
    }

    public boolean belongsTo(CustomerId candidate) {
        return customerId.equals(candidate);
    }

    /**
     * Devuelve los eventos pendientes y los limpia, para que se publiquen una sola vez.
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> pending = List.copyOf(domainEvents);
        domainEvents.clear();
        return pending;
    }

    private void transitionTo(OrderStatus target, Clock clock) {
        ensureCanTransitionTo(target);
        this.status = target;
        this.updatedAt = clock.instant();
    }

    private void ensureCanTransitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderStateException(id, status, target);
        }
    }

    private void record(DomainEvent event) {
        domainEvents.add(event);
    }

    public OrderId id() {
        return id;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public List<OrderItem> items() {
        return items;
    }

    public Money subtotal() {
        return subtotal;
    }

    public Money discount() {
        return discount;
    }

    public Money total() {
        return total;
    }

    public OrderStatus status() {
        return status;
    }

    public String paymentReference() {
        return paymentReference;
    }

    public String cancellationReason() {
        return cancellationReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long version() {
        return version;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Patrón Builder: usado por {@link #create} y por los adaptadores de persistencia
     * para reconstruir (rehidratar) un pedido existente sin exponer setters.
     */
    public static final class Builder {

        private OrderId id;
        private CustomerId customerId;
        private List<OrderItem> items;
        private Money subtotal;
        private Money discount;
        private Money total;
        private OrderStatus status;
        private String paymentReference;
        private String cancellationReason;
        private Instant createdAt;
        private Instant updatedAt;
        private Long version;

        private Builder() {
        }

        public Builder id(OrderId id) {
            this.id = id;
            return this;
        }

        public Builder customerId(CustomerId customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder items(List<OrderItem> items) {
            this.items = items;
            return this;
        }

        public Builder subtotal(Money subtotal) {
            this.subtotal = subtotal;
            return this;
        }

        public Builder discount(Money discount) {
            this.discount = discount;
            return this;
        }

        public Builder total(Money total) {
            this.total = total;
            return this;
        }

        public Builder status(OrderStatus status) {
            this.status = status;
            return this;
        }

        public Builder paymentReference(String paymentReference) {
            this.paymentReference = paymentReference;
            return this;
        }

        public Builder cancellationReason(String cancellationReason) {
            this.cancellationReason = cancellationReason;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder version(Long version) {
            this.version = version;
            return this;
        }

        public Order build() {
            return new Order(this);
        }
    }
}
