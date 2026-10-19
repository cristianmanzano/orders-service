package com.cmanzano.orders.domain.policy;

import com.cmanzano.orders.domain.model.Money;

import java.math.BigDecimal;

/**
 * Fábrica (Factory Method) con las políticas de descuento vigentes del negocio.
 */
public final class DiscountPolicies {

    private DiscountPolicies() {
    }

    /**
     * 10% al comprar 10 o más unidades, o 5% si el subtotal es de al menos 500 USD;
     * se aplica el mayor de los dos.
     */
    public static DiscountPolicy standard() {
        return DiscountPolicy.bestOf(
                new VolumeDiscountPolicy(10, new BigDecimal("10")),
                new ThresholdDiscountPolicy(Money.of("500.00", "USD"), new BigDecimal("5")));
    }
}
