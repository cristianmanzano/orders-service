package com.cmanzano.orders.domain.model;

import com.cmanzano.orders.domain.exception.InvalidOrderException;

import java.util.Objects;

/**
 * Línea de pedido. El nombre y el precio son una "foto" del catálogo al momento de la compra,
 * de modo que cambios posteriores en el catálogo no alteran pedidos históricos.
 */
public record OrderItem(String sku, String name, int quantity, Money unitPrice) {

    public static final int MAX_QUANTITY_PER_ITEM = 100;

    public OrderItem {
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(unitPrice, "unit price is required");
        if (quantity < 1 || quantity > MAX_QUANTITY_PER_ITEM) {
            throw new InvalidOrderException(
                    "Quantity for %s must be between 1 and %d".formatted(sku, MAX_QUANTITY_PER_ITEM));
        }
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
