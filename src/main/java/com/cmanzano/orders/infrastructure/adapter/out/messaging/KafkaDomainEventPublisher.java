package com.cmanzano.orders.infrastructure.adapter.out.messaging;

import com.cmanzano.orders.application.port.out.DomainEventPublisherPort;
import com.cmanzano.orders.domain.event.DomainEvent;
import com.cmanzano.orders.infrastructure.messaging.OrderEventMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Publica los eventos de dominio en Kafka usando el ID del pedido como clave: todos los eventos
 * de un mismo pedido caen en la misma partición y se consumen en orden.
 * <p>
 * Limitación conocida: la publicación ocurre después del commit en PostgreSQL. Si Kafka falla,
 * el evento se registra como error y se incrementa una métrica. La evolución natural es el
 * patrón Transactional Outbox (ver README).
 */
@Component
public class KafkaDomainEventPublisher implements DomainEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaDomainEventPublisher.class);

    private final KafkaTemplate<String, OrderEventMessage> kafkaTemplate;
    private final String topic;
    private final Counter publishFailures;

    public KafkaDomainEventPublisher(KafkaTemplate<String, OrderEventMessage> kafkaTemplate,
                                     @Value("${app.kafka.order-events-topic}") String topic,
                                     MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishFailures = Counter.builder("orders.events.publish.failures")
                .description("Domain events that could not be published to Kafka")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> publish(List<DomainEvent> events) {
        return Flux.fromIterable(events)
                .map(OrderEventMessage::from)
                .concatMap(this::send)
                .then();
    }

    /**
     * {@code KafkaTemplate.send} puede bloquear mientras obtiene metadatos del clúster, por eso
     * se ejecuta en el scheduler boundedElastic y nunca en el event loop de Netty.
     */
    private Mono<Void> send(OrderEventMessage message) {
        return Mono.fromFuture(() -> kafkaTemplate.send(topic, message.orderId(), message))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(result -> log.debug("Published {} for order {} to partition {}", message.eventType(),
                        message.orderId(), result.getRecordMetadata().partition()))
                .then()
                .onErrorResume(ex -> {
                    publishFailures.increment();
                    log.error("Could not publish {} for order {}", message.eventType(), message.orderId(), ex);
                    return Mono.empty();
                });
    }
}
