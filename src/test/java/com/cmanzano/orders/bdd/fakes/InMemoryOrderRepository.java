package com.cmanzano.orders.bdd.fakes;

import com.cmanzano.orders.application.port.out.OrderRepositoryPort;
import com.cmanzano.orders.domain.model.CustomerId;
import com.cmanzano.orders.domain.model.Order;
import com.cmanzano.orders.domain.model.OrderId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake del puerto de persistencia: guarda copias (como haría una base de datos), de modo que
 * mutar un pedido cargado no altera lo almacenado hasta que se llama a {@link #save}.
 */
public class InMemoryOrderRepository implements OrderRepositoryPort {

    private final Map<OrderId, Order> storage = new ConcurrentHashMap<>();

    @Override
    public Mono<Order> save(Order order) {
        long nextVersion = order.version() == null ? 0 : order.version() + 1;
        Order stored = copy(order, nextVersion);
        storage.put(order.id(), stored);
        return Mono.just(copy(stored, nextVersion));
    }

    @Override
    public Mono<Order> findById(OrderId orderId) {
        return Mono.justOrEmpty(storage.get(orderId)).map(order -> copy(order, order.version()));
    }

    @Override
    public Flux<Order> findByCustomerId(CustomerId customerId) {
        return Flux.fromIterable(storage.values()).filter(order -> order.belongsTo(customerId));
    }

    private static Order copy(Order order, Long version) {
        return Order.builder()
                .id(order.id())
                .customerId(order.customerId())
                .items(order.items())
                .subtotal(order.subtotal())
                .discount(order.discount())
                .total(order.total())
                .status(order.status())
                .paymentReference(order.paymentReference())
                .cancellationReason(order.cancellationReason())
                .createdAt(order.createdAt())
                .updatedAt(order.updatedAt())
                .version(version)
                .build();
    }
}
