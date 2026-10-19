package com.cmanzano.orders.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    private static final int PARTITIONS = 3;

    @Bean
    NewTopic orderEventsTopic(@Value("${app.kafka.order-events-topic}") String topic,
                              @Value("${app.kafka.replication-factor:1}") int replicationFactor) {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(replicationFactor).build();
    }

    @Bean
    NewTopic orderEventsDeadLetterTopic(@Value("${app.kafka.order-events-topic}") String topic,
                                        @Value("${app.kafka.replication-factor:1}") int replicationFactor) {
        return TopicBuilder.name(topic + ".DLT").partitions(PARTITIONS).replicas(replicationFactor).build();
    }

    /**
     * Reintenta con backoff exponencial y, si el mensaje sigue fallando, lo envía a la
     * Dead Letter Topic ({@code <topic>.DLT}) para no bloquear la partición.
     * Los mensajes que no se pueden deserializar ("poison pills") van directo a la DLT.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxAttempts(3);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);
        errorHandler.addNotRetryableExceptions(DeserializationException.class);
        return errorHandler;
    }
}
