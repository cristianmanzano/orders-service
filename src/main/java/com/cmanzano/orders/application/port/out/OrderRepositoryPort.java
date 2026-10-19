package com.cmanzano.orders.application.port.out;

import com.cmanzano.orders.domain.model.CustomerId;
import com.cmanzano.orders.domain.model.Order;
import com.cmanzano.orders.domain.model.OrderId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderRepositoryPort {

    Mono<Order> save(Order order);

    Mono<Order> findById(OrderId orderId);

    Flux<Order> findByCustomerId(CustomerId customerId);
}
