package com.cmanzano.orders.infrastructure.adapter.in.messaging;

import com.cmanzano.orders.application.port.in.RecordOrderAuditUseCase;
import com.cmanzano.orders.domain.model.OrderAuditEntry;
import com.cmanzano.orders.domain.model.OrderId;
import com.cmanzano.orders.infrastructure.messaging.OrderEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Adaptador de entrada por mensajería: consume los eventos de pedidos y construye la traza de
 * auditoría en DynamoDB (proyección / modelo de lectura al estilo CQRS).
 * <p>
 * El hilo del listener pertenece a Spring Kafka (no al event loop), así que esperar el resultado
 * reactivo aquí es seguro y permite que un fallo dispare los reintentos y la DLT.
 */
@Component
public class OrderEventsKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsKafkaListener.class);
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10);

    private final RecordOrderAuditUseCase recordOrderAudit;

    public OrderEventsKafkaListener(RecordOrderAuditUseCase recordOrderAudit) {
        this.recordOrderAudit = recordOrderAudit;
    }

    @KafkaListener(topics = "${app.kafka.order-events-topic}", groupId = "orders-audit")
    public void onOrderEvent(OrderEventMessage message) {
        log.info("Received {} for order {}", message.eventType(), message.orderId());
        OrderAuditEntry entry = new OrderAuditEntry(OrderId.of(message.orderId()), message.eventId(),
                message.eventType(), message.detail(), message.occurredAt());
        recordOrderAudit.record(entry).block(WRITE_TIMEOUT);
    }
}
