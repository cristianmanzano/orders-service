package com.cmanzano.orders.domain.policy;

import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.OrderItem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountPolicyTest {

    private final DiscountPolicy standard = DiscountPolicies.standard();

    @ParameterizedTest(name = "{0} unidades a {1} USD -> descuento {2} USD")
    @CsvSource({
            "1,   25.00,   0.00",   // sin descuento
            "9,   25.00,   0.00",   // por debajo de ambos umbrales
            "10,  25.00,  25.00",   // volumen: 10% de 250
            "2,  300.00,  30.00",   // umbral: 5% de 600
            "12,  50.00,  60.00"    // ambos aplican: gana volumen (10% de 600 > 5% de 600)
    })
    void appliesTheBestAvailableDiscount(int quantity, String unitPrice, String expectedDiscount) {
        List<OrderItem> items = List.of(new OrderItem("SKU-1", "Item", quantity, Money.of(unitPrice, "USD")));
        Money subtotal = items.getFirst().subtotal();

        assertThat(standard.discountFor(subtotal, items)).isEqualTo(Money.of(expectedDiscount, "USD"));
    }
}
