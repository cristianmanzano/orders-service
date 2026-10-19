package com.cmanzano.orders.bdd.fakes;

import com.cmanzano.orders.application.port.out.PaymentGatewayPort;
import reactor.core.publisher.Mono;

public class StubPaymentGateway implements PaymentGatewayPort {

    private boolean approve = true;

    public void approveCharges() {
        approve = true;
    }

    public void rejectCharges() {
        approve = false;
    }

    @Override
    public Mono<PaymentResult> charge(PaymentRequest request) {
        return Mono.just(approve
                ? PaymentResult.ofApproved("PAY-" + request.orderId().value().toString().substring(0, 8))
                : PaymentResult.ofRejected("Insufficient funds"));
    }
}
