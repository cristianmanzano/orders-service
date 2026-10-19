package com.cmanzano.orders.infrastructure.adapter.out.persistence.dynamodb;

import com.cmanzano.orders.application.port.out.OrderAuditRepositoryPort;
import com.cmanzano.orders.domain.model.OrderAuditEntry;
import com.cmanzano.orders.domain.model.OrderId;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Usa el cliente asíncrono del SDK v2 (CompletableFuture / Publisher) y lo adapta a Reactor,
 * de modo que ninguna llamada a DynamoDB bloquea el event loop de WebFlux.
 */
@Component
public class DynamoDbOrderAuditAdapter implements OrderAuditRepositoryPort {

    /**
     * Ancho fijo (nanosegundos siempre presentes) para que el orden lexicográfico de la sort key
     * coincida con el cronológico; Instant.toString() omite ceros y rompería ese orden.
     */
    private static final DateTimeFormatter SORTABLE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC);

    private final DynamoDbAsyncTable<OrderAuditItem> table;

    public DynamoDbOrderAuditAdapter(DynamoDbAsyncTable<OrderAuditItem> table) {
        this.table = table;
    }

    @Override
    public Mono<Void> save(OrderAuditEntry entry) {
        return Mono.fromFuture(() -> table.putItem(toItem(entry)));
    }

    @Override
    public Flux<OrderAuditEntry> findByOrderId(OrderId orderId) {
        QueryConditional byOrder = QueryConditional.keyEqualTo(Key.builder().partitionValue(orderId.toString()).build());
        return Flux.from(table.query(byOrder).items()).map(DynamoDbOrderAuditAdapter::toDomain);
    }

    private static OrderAuditItem toItem(OrderAuditEntry entry) {
        OrderAuditItem item = new OrderAuditItem();
        item.setOrderId(entry.orderId().toString());
        item.setSortKey(SORTABLE_TIMESTAMP.format(entry.occurredAt()) + "#" + entry.eventId());
        item.setEventId(entry.eventId().toString());
        item.setEventType(entry.eventType());
        item.setDetail(entry.detail());
        item.setOccurredAt(entry.occurredAt().toString());
        return item;
    }

    private static OrderAuditEntry toDomain(OrderAuditItem item) {
        return new OrderAuditEntry(OrderId.of(item.getOrderId()), UUID.fromString(item.getEventId()),
                item.getEventType(), item.getDetail(), Instant.parse(item.getOccurredAt()));
    }
}
