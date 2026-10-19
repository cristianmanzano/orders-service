package com.cmanzano.orders.infrastructure.adapter.in.web;

import com.cmanzano.orders.application.exception.PaymentGatewayUnavailableException;
import com.cmanzano.orders.domain.exception.InvalidOrderException;
import com.cmanzano.orders.domain.exception.InvalidOrderStateException;
import com.cmanzano.orders.domain.exception.OrderNotFoundException;
import com.cmanzano.orders.domain.exception.PaymentRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.util.List;
import java.util.Map;

/**
 * Traduce excepciones a respuestas RFC 9457 (ProblemDetail). Nunca se exponen trazas ni
 * detalles internos al cliente (OWASP A05 - Security Misconfiguration).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleNotFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Order not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    ProblemDetail handleInvalidState(InvalidOrderStateException ex) {
        return problem(HttpStatus.CONFLICT, "Invalid order state", ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderException.class)
    ProblemDetail handleInvalidOrder(InvalidOrderException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid order", ex.getMessage());
    }

    @ExceptionHandler(PaymentRejectedException.class)
    ProblemDetail handlePaymentRejected(PaymentRejectedException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Payment rejected", ex.getMessage());
    }

    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    ProblemDetail handlePaymentUnavailable(PaymentGatewayUnavailableException ex) {
        log.warn("Payment gateway unavailable: {}", ex.getCause() != null ? ex.getCause().toString() : ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider unavailable",
                "The payment provider is temporarily unavailable, please try again later");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The order was modified by another request, please retry");
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ProblemDetail handleValidation(WebExchangeBindException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "The request contains invalid fields");
        List<Map<String, String>> errors = ex.getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ServerWebInputException.class)
    ProblemDetail handleBadInput(ServerWebInputException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "The request could not be read");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid credentials");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to perform this operation");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : "Request failed";
        return problem(ex.getStatusCode(), reason, reason);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred");
    }

    private static ProblemDetail problem(HttpStatusCode status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
