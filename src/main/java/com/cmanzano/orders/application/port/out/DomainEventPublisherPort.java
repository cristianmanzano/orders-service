package com.cmanzano.orders.application.port.out;

import com.cmanzano.orders.domain.event.DomainEvent;
import reactor.core.publisher.Mono;

import java.util.List;

public interface DomainEventPublisherPort {

    Mono<Void> publish(List<DomainEvent> events);
}
