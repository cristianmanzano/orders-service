package com.cmanzano.orders.application.port.in;

import com.cmanzano.orders.domain.model.CustomerId;
import com.cmanzano.orders.domain.model.Order;

/**
 * Identidad de quien invoca un caso de uso, independiente del mecanismo de autenticación
 * (JWT, sesión, mensaje...). Centraliza la regla de control de acceso a pedidos (OWASP A01).
 */
public record Requester(String customerId, boolean admin) {

    public static Requester customer(String customerId) {
        return new Requester(customerId, false);
    }

    public CustomerId asCustomerId() {
        return new CustomerId(customerId);
    }

    public boolean canAccess(Order order) {
        return admin || order.belongsTo(asCustomerId());
    }
}
