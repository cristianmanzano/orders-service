package com.cmanzano.orders.domain.policy;

import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.OrderItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * Descuento porcentual cuando el total de unidades del pedido alcanza un mínimo.
 */
public record VolumeDiscountPolicy(int minimumUnits, BigDecimal percent) implements DiscountPolicy {

    @Override
    public Money discountFor(Money subtotal, List<OrderItem> items) {
        int units = items.stream().mapToInt(OrderItem::quantity).sum();
        return units >= minimumUnits ? subtotal.percentage(percent) : Money.zero(subtotal.currency());
    }
}
