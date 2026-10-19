package com.cmanzano.orders.domain.exception;

import com.cmanzano.orders.domain.model.OrderId;

public class OrderNotFoundException extends DomainException {

    public OrderNotFoundException(OrderId orderId) {
        super("Order %s not found".formatted(orderId));
    }
}
