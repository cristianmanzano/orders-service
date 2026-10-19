package com.cmanzano.orders.application.port.in;

import com.cmanzano.orders.domain.model.OrderAuditEntry;
import com.cmanzano.orders.domain.model.OrderId;
import reactor.core.publisher.Flux;

public interface GetOrderAuditUseCase {

    Flux<OrderAuditEntry> getAuditTrail(OrderId orderId);
}
