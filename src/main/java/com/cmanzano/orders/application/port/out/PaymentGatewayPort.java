package com.cmanzano.orders.application.port.out;

import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.OrderId;
import reactor.core.publisher.Mono;

public interface PaymentGatewayPort {

    /**
     * Cobra el importe indicado. Emite {@code PaymentGatewayUnavailableException} si el proveedor
     * no responde; un rechazo de negocio se devuelve como {@link PaymentResult} no aprobado.
     */
    Mono<PaymentResult> charge(PaymentRequest request);

    record PaymentRequest(OrderId orderId, Money amount) {
    }

    record PaymentResult(boolean approved, String reference, String reason) {

        public static PaymentResult ofApproved(String reference) {
            return new PaymentResult(true, reference, null);
        }

        public static PaymentResult ofRejected(String reason) {
            return new PaymentResult(false, null, reason);
        }
    }
}
