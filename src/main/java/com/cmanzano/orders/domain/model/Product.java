package com.cmanzano.orders.domain.model;

import java.util.Objects;

public record Product(String sku, String name, Money price) {

    public Product {
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(price, "price is required");
    }
}
