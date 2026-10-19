package com.cmanzano.orders.infrastructure.adapter.out.persistence.postgres;

import com.cmanzano.orders.domain.model.CustomerId;
import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.Order;
import com.cmanzano.orders.domain.model.OrderId;
import com.cmanzano.orders.domain.model.OrderItem;
import com.cmanzano.orders.domain.model.OrderStatus;
import com.cmanzano.orders.domain.model.Product;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

final class OrderPersistenceMapper {

    private OrderPersistenceMapper() {
    }

    static OrderEntity toEntity(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setId(order.id().value());
        entity.setCustomerId(order.customerId().value());
        entity.setStatus(order.status().name());
        entity.setCurrency(order.total().currency());
        entity.setSubtotal(order.subtotal().amount());
        entity.setDiscount(order.discount().amount());
        entity.setTotal(order.total().amount());
        entity.setPaymentReference(order.paymentReference());
        entity.setCancellationReason(order.cancellationReason());
        entity.setCreatedAt(toOffset(order.createdAt()));
        entity.setUpdatedAt(toOffset(order.updatedAt()));
        entity.setVersion(order.version());
        return entity;
    }

    static List<OrderItemEntity> toItemEntities(Order order) {
        return order.items().stream().map(item -> {
            OrderItemEntity entity = new OrderItemEntity();
            entity.setOrderId(order.id().value());
            entity.setSku(item.sku());
            entity.setName(item.name());
            entity.setQuantity(item.quantity());
            entity.setUnitPrice(item.unitPrice().amount());
            return entity;
        }).toList();
    }

    static Order toDomain(OrderEntity entity, List<OrderItemEntity> itemEntities) {
        String currency = entity.getCurrency();
        List<OrderItem> items = itemEntities.stream()
                .map(item -> new OrderItem(item.getSku(), item.getName(), item.getQuantity(),
                        Money.of(item.getUnitPrice(), currency)))
                .toList();
        return Order.builder()
                .id(new OrderId(entity.getId()))
                .customerId(new CustomerId(entity.getCustomerId()))
                .items(items)
                .subtotal(Money.of(entity.getSubtotal(), currency))
                .discount(Money.of(entity.getDiscount(), currency))
                .total(Money.of(entity.getTotal(), currency))
                .status(OrderStatus.valueOf(entity.getStatus()))
                .paymentReference(entity.getPaymentReference())
                .cancellationReason(entity.getCancellationReason())
                .createdAt(entity.getCreatedAt().toInstant())
                .updatedAt(entity.getUpdatedAt().toInstant())
                .version(entity.getVersion())
                .build();
    }

    static Product toDomain(ProductEntity entity) {
        return new Product(entity.getSku(), entity.getName(), Money.of(entity.getPrice(), entity.getCurrency()));
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
