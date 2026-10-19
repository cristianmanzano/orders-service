package com.cmanzano.orders.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object inmutable que representa una cantidad monetaria en una moneda ISO-4217.
 * Toda operación devuelve una nueva instancia (sin efectos secundarios).
 */
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {

    private static final int SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative");
        }
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be an ISO-4217 code");
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public Money percentage(BigDecimal percent) {
        return new Money(amount.multiply(percent).divide(ONE_HUNDRED, SCALE, RoundingMode.HALF_EVEN), currency);
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return compareTo(other) >= 0;
    }

    public boolean hasSameCurrencyAs(Money other) {
        return currency.equals(other.currency);
    }

    @Override
    public int compareTo(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void assertSameCurrency(Money other) {
        if (!hasSameCurrencyAs(other)) {
            throw new IllegalArgumentException("Currency mismatch: %s vs %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
