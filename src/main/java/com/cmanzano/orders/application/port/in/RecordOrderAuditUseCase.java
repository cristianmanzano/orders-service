package com.cmanzano.orders.application.port.in;

import com.cmanzano.orders.domain.model.OrderAuditEntry;
import reactor.core.publisher.Mono;

public interface RecordOrderAuditUseCase {

    Mono<Void> record(OrderAuditEntry entry);
}
