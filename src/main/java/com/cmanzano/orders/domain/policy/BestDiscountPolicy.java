package com.cmanzano.orders.domain.policy;

import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.OrderItem;

import java.util.Comparator;
import java.util.List;

/**
 * Patrón Composite: trata un conjunto de políticas como una sola y aplica la más
 * favorable para el cliente (los descuentos no son acumulables).
 */
public record BestDiscountPolicy(List<DiscountPolicy> policies) implements DiscountPolicy {

    public BestDiscountPolicy {
        policies = List.copyOf(policies);
    }

    @Override
    public Money discountFor(Money subtotal, List<OrderItem> items) {
        return policies.stream()
                .map(policy -> policy.discountFor(subtotal, items))
                .max(Comparator.naturalOrder())
                .orElseGet(() -> Money.zero(subtotal.currency()));
    }
}
