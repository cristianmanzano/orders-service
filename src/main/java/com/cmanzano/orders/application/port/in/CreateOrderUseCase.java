package com.cmanzano.orders.application.port.in;

import com.cmanzano.orders.domain.model.Order;
import reactor.core.publisher.Mono;

import java.util.List;

public interface CreateOrderUseCase {

    Mono<Order> create(CreateOrderCommand command);

    /**
     * El cliente solo envía SKU y cantidad: el precio siempre se toma del catálogo
     * (nunca se confía en precios enviados por el cliente).
     */
    record CreateOrderCommand(String customerId, List<OrderLine> lines) {

        public CreateOrderCommand {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    record OrderLine(String sku, int quantity) {
    }
}
