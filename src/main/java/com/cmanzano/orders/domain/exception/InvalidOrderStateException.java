package com.cmanzano.orders.domain.exception;

import com.cmanzano.orders.domain.model.OrderId;
import com.cmanzano.orders.domain.model.OrderStatus;

public class InvalidOrderStateException extends DomainException {

    public InvalidOrderStateException(OrderId orderId, OrderStatus current, OrderStatus target) {
        super("Order %s cannot transition from %s to %s".formatted(orderId, current, target));
    }
}
