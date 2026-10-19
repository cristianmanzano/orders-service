package com.cmanzano.orders.domain.policy;

import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.OrderItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * Descuento porcentual cuando el subtotal supera un umbral en la misma moneda.
 */
public record ThresholdDiscountPolicy(Money threshold, BigDecimal percent) implements DiscountPolicy {

    @Override
    public Money discountFor(Money subtotal, List<OrderItem> items) {
        boolean applies = subtotal.hasSameCurrencyAs(threshold) && subtotal.isGreaterThanOrEqual(threshold);
        return applies ? subtotal.percentage(percent) : Money.zero(subtotal.currency());
    }
}
