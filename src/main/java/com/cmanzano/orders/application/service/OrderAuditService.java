package com.cmanzano.orders.application.service;

import com.cmanzano.orders.application.port.in.GetOrderAuditUseCase;
import com.cmanzano.orders.application.port.in.RecordOrderAuditUseCase;
import com.cmanzano.orders.application.port.out.OrderAuditRepositoryPort;
import com.cmanzano.orders.domain.model.OrderAuditEntry;
import com.cmanzano.orders.domain.model.OrderId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class OrderAuditService implements RecordOrderAuditUseCase, GetOrderAuditUseCase {

    private final OrderAuditRepositoryPort auditRepository;

    public OrderAuditService(OrderAuditRepositoryPort auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Override
    public Mono<Void> record(OrderAuditEntry entry) {
        return auditRepository.save(entry);
    }

    @Override
    public Flux<OrderAuditEntry> getAuditTrail(OrderId orderId) {
        return auditRepository.findByOrderId(orderId);
    }
}
