package com.cmanzano.orders.application.port.out;

import com.cmanzano.orders.domain.model.OrderAuditEntry;
import com.cmanzano.orders.domain.model.OrderId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderAuditRepositoryPort {

    Mono<Void> save(OrderAuditEntry entry);

    Flux<OrderAuditEntry> findByOrderId(OrderId orderId);
}
