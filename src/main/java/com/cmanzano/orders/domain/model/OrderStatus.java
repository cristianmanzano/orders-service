package com.cmanzano.orders.domain.model;

/**
 * Máquina de estados del pedido. Cada constante define su propio comportamiento
 * (polimorfismo por constante del enum) en lugar de un switch disperso por el código.
 */
public enum OrderStatus {

    CREATED {
        @Override
        public boolean canTransitionTo(OrderStatus target) {
            return target == PAID || target == CANCELLED;
        }
    },
    PAID {
        @Override
        public boolean canTransitionTo(OrderStatus target) {
            return false;
        }
    },
    CANCELLED {
        @Override
        public boolean canTransitionTo(OrderStatus target) {
            return false;
        }
    };

    public abstract boolean canTransitionTo(OrderStatus target);
}
