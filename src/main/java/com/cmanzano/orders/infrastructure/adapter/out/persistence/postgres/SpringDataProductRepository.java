package com.cmanzano.orders.infrastructure.adapter.out.persistence.postgres;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

interface SpringDataProductRepository extends ReactiveCrudRepository<ProductEntity, String> {
}
