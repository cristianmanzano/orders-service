package com.cmanzano.orders.domain.policy;

import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.OrderItem;

import java.util.List;

/**
 * Patrón Strategy: cada política encapsula una regla de descuento intercambiable.
 * Al ser una interfaz funcional, una política también puede expresarse como lambda.
 */
@FunctionalInterface
public interface DiscountPolicy {

    Money discountFor(Money subtotal, List<OrderItem> items);

    static DiscountPolicy none() {
        return (subtotal, items) -> Money.zero(subtotal.currency());
    }

    static DiscountPolicy bestOf(DiscountPolicy... policies) {
        return new BestDiscountPolicy(List.of(policies));
    }
}
