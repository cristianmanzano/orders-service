package com.cmanzano.orders.application.port.in;

import com.cmanzano.orders.domain.model.Order;
import reactor.core.publisher.Flux;

public interface ListOrdersUseCase {

    Flux<Order> listOrders(Requester requester);
}
