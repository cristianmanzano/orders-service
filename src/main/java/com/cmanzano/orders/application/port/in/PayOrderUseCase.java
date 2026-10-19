package com.cmanzano.orders.application.port.in;

import com.cmanzano.orders.domain.model.Order;
import com.cmanzano.orders.domain.model.OrderId;
import reactor.core.publisher.Mono;

public interface PayOrderUseCase {

    Mono<Order> pay(OrderId orderId, Requester requester);
}
