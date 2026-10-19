package com.cmanzano.orders.infrastructure.adapter.out.persistence.postgres;

import com.cmanzano.orders.application.port.out.OrderRepositoryPort;
import com.cmanzano.orders.domain.model.CustomerId;
import com.cmanzano.orders.domain.model.Order;
import com.cmanzano.orders.domain.model.OrderId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Adaptador de salida (patrón Adapter) que implementa el puerto del dominio sobre R2DBC.
 */
@Component
public class PostgresOrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataOrderRepository orders;
    private final SpringDataOrderItemRepository orderItems;
    private final TransactionalOperator transactionalOperator;

    PostgresOrderRepositoryAdapter(SpringDataOrderRepository orders,
                                   SpringDataOrderItemRepository orderItems,
                                   TransactionalOperator transactionalOperator) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.transactionalOperator = transactionalOperator;
    }

    /**
     * Pedido y líneas se guardan en una única transacción reactiva. Las líneas son inmutables
     * tras la creación, así que solo se insertan cuando el pedido es nuevo (version == null).
     */
    @Override
    public Mono<Order> save(Order order) {
        OrderEntity entity = OrderPersistenceMapper.toEntity(order);
        boolean isNew = entity.getVersion() == null;
        return orders.save(entity)
                .flatMap(saved -> (isNew
                        ? orderItems.saveAll(OrderPersistenceMapper.toItemEntities(order))
                        : orderItems.findByOrderId(saved.getId()))
                        .collectList()
                        .map(items -> OrderPersistenceMapper.toDomain(saved, items)))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Order> findById(OrderId orderId) {
        return orders.findById(orderId.value()).flatMap(this::withItems);
    }

    @Override
    public Flux<Order> findByCustomerId(CustomerId customerId) {
        return orders.findByCustomerIdOrderByCreatedAtDesc(customerId.value()).concatMap(this::withItems);
    }

    private Mono<Order> withItems(OrderEntity entity) {
        return orderItems.findByOrderId(entity.getId())
                .collectList()
                .map(items -> OrderPersistenceMapper.toDomain(entity, items));
    }
}
