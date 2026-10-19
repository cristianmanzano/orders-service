package com.cmanzano.orders.domain.exception;

public class InvalidOrderException extends DomainException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
