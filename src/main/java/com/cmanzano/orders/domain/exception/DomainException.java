package com.cmanzano.orders.domain.exception;

/**
 * Base abstracta de las violaciones de reglas de negocio.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
