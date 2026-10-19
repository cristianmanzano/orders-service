package com.cmanzano.orders.bdd.fakes;

import com.cmanzano.orders.application.port.out.DomainEventPublisherPort;
import com.cmanzano.orders.domain.event.DomainEvent;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

public class RecordingEventPublisher implements DomainEventPublisherPort {

    private final List<DomainEvent> published = new ArrayList<>();

    @Override
    public Mono<Void> publish(List<DomainEvent> events) {
        return Mono.fromRunnable(() -> published.addAll(events));
    }

    public List<DomainEvent> published() {
        return List.copyOf(published);
    }

    public void clear() {
        published.clear();
    }
}
