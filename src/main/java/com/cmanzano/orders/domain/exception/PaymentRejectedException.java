package com.cmanzano.orders.domain.exception;

import com.cmanzano.orders.domain.model.OrderId;

public class PaymentRejectedException extends DomainException {

    public PaymentRejectedException(OrderId orderId, String reason) {
        super("Payment for order %s was rejected: %s".formatted(orderId, reason));
    }
}
