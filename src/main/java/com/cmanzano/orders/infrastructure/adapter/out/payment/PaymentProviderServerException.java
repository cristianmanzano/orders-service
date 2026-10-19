package com.cmanzano.orders.infrastructure.adapter.out.payment;

/**
 * Error 5xx del proveedor: es transitorio, por lo que se reintenta y cuenta para el circuit breaker.
 */
public class PaymentProviderServerException extends RuntimeException {

    public PaymentProviderServerException(int statusCode) {
        super("Payment provider responded with HTTP " + statusCode);
    }
}
