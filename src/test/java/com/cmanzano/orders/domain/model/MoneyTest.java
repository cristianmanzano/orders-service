package com.cmanzano.orders.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalizesScaleToTwoDecimals() {
        assertThat(Money.of("10.5", "USD").amount()).isEqualByComparingTo("10.50");
        assertThat(Money.of("10.5", "USD")).isEqualTo(Money.of("10.50", "USD"));
    }

    @Test
    void addsAndMultipliesWithoutMutatingOriginal() {
        Money price = Money.of("25.00", "USD");

        Money total = price.multiply(3).add(Money.of("5.00", "USD"));

        assertThat(total).isEqualTo(Money.of("80.00", "USD"));
        assertThat(price).isEqualTo(Money.of("25.00", "USD"));
    }

    @Test
    void calculatesPercentageWithBankersRounding() {
        assertThat(Money.of("100.05", "USD").percentage(new BigDecimal("10"))).isEqualTo(Money.of("10.00", "USD"));
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> Money.of("-1", "USD")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOperationsBetweenDifferentCurrencies() {
        assertThatThrownBy(() -> Money.of("1", "USD").add(Money.of("1", "COP")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }
}
