package com.cmanzano.orders.application.exception;

/**
 * El proveedor de pagos no está disponible (timeout, errores 5xx o circuit breaker abierto).
 * Es un fallo técnico recuperable, distinto de un rechazo de negocio.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
