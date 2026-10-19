package com.cmanzano.orders.domain.model;

public record CustomerId(String value) {

    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("customer id is required");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
