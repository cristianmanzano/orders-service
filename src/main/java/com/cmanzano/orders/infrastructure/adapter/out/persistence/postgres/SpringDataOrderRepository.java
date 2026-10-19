package com.cmanzano.orders.infrastructure.adapter.out.persistence.postgres;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

interface SpringDataOrderRepository extends ReactiveCrudRepository<OrderEntity, UUID> {

    Flux<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
