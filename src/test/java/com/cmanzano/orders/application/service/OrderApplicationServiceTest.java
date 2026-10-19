package com.cmanzano.orders.application.service;

import com.cmanzano.orders.application.exception.PaymentGatewayUnavailableException;
import com.cmanzano.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.cmanzano.orders.application.port.in.CreateOrderUseCase.OrderLine;
import com.cmanzano.orders.application.port.in.Requester;
import com.cmanzano.orders.application.port.out.DomainEventPublisherPort;
import com.cmanzano.orders.application.port.out.OrderRepositoryPort;
import com.cmanzano.orders.application.port.out.PaymentGatewayPort;
import com.cmanzano.orders.application.port.out.PaymentGatewayPort.PaymentRequest;
import com.cmanzano.orders.application.port.out.PaymentGatewayPort.PaymentResult;
import com.cmanzano.orders.application.port.out.ProductCatalogPort;
import com.cmanzano.orders.domain.event.DomainEvent;
import com.cmanzano.orders.domain.event.OrderCreated;
import com.cmanzano.orders.domain.event.OrderPaid;
import com.cmanzano.orders.domain.exception.InvalidOrderException;
import com.cmanzano.orders.domain.exception.InvalidOrderStateException;
import com.cmanzano.orders.domain.exception.OrderNotFoundException;
import com.cmanzano.orders.domain.exception.PaymentRejectedException;
import com.cmanzano.orders.domain.model.CustomerId;
import com.cmanzano.orders.domain.model.Money;
import com.cmanzano.orders.domain.model.Order;
import com.cmanzano.orders.domain.model.OrderItem;
import com.cmanzano.orders.domain.model.OrderStatus;
import com.cmanzano.orders.domain.model.Product;
import com.cmanzano.orders.domain.policy.DiscountPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
    private static final Requester ALICE = Requester.customer("alice");
    private static final Requester BOB = Requester.customer("bob");

    @Mock
    private OrderRepositoryPort orderRepository;
    @Mock
    private ProductCatalogPort productCatalog;
    @Mock
    private PaymentGatewayPort paymentGateway;
    @Mock
    private DomainEventPublisherPort eventPublisher;
    @Captor
    private ArgumentCaptor<List<DomainEvent>> eventsCaptor;

    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        service = new OrderApplicationService(orderRepository, productCatalog, paymentGateway, eventPublisher,
                DiscountPolicy.none(), CLOCK);
    }

    private void repositoryReturnsWhatItSaves() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(eventPublisher.publish(anyList())).thenReturn(Mono.empty());
    }

    private static Order existingOrderOf(String customer) {
        Order order = Order.create(new CustomerId(customer),
                List.of(new OrderItem("MONITOR-27", "Monitor", 1, Money.of("300.00", "USD"))),
                DiscountPolicy.none(), CLOCK);
        order.pullDomainEvents();
        return order;
    }

    @Test
    void createsAnOrderUsingCatalogPricesAndPublishesTheEvent() {
        when(productCatalog.findBySkus(anySet()))
                .thenReturn(Flux.just(new Product("MOUSE-01", "Mouse", Money.of("25.00", "USD"))));
        repositoryReturnsWhatItSaves();

        var command = new CreateOrderCommand("alice", List.of(new OrderLine("MOUSE-01", 2)));

        StepVerifier.create(service.create(command))
                .assertNext(order -> {
                    assertThat(order.customerId()).isEqualTo(new CustomerId("alice"));
                    assertThat(order.total()).isEqualTo(Money.of("50.00", "USD"));
                    assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
                })
                .verifyComplete();

        verify(eventPublisher).publish(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).singleElement().isInstanceOf(OrderCreated.class);
    }

    @Test
    void rejectsUnknownProductsWithoutPersistingAnything() {
        when(productCatalog.findBySkus(anySet())).thenReturn(Flux.empty());

        var command = new CreateOrderCommand("alice", List.of(new OrderLine("NOPE-01", 1)));

        StepVerifier.create(service.create(command))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidOrderException.class)
                        .hasMessage("Unknown product: NOPE-01"))
                .verify();

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectsDuplicatedSkus() {
        var command = new CreateOrderCommand("alice",
                List.of(new OrderLine("MOUSE-01", 1), new OrderLine("MOUSE-01", 2)));

        StepVerifier.create(service.create(command)).expectError(InvalidOrderException.class).verify();

        verifyNoInteractions(productCatalog, orderRepository);
    }

    @Test
    void customerCannotSeeOrdersFromSomeoneElse() {
        Order bobsOrder = existingOrderOf("bob");
        when(orderRepository.findById(bobsOrder.id())).thenReturn(Mono.just(bobsOrder));

        StepVerifier.create(service.getOrder(bobsOrder.id(), ALICE))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void adminCanSeeAnyOrder() {
        Order bobsOrder = existingOrderOf("bob");
        when(orderRepository.findById(bobsOrder.id())).thenReturn(Mono.just(bobsOrder));

        StepVerifier.create(service.getOrder(bobsOrder.id(), new Requester("admin", true)))
                .expectNext(bobsOrder)
                .verifyComplete();
    }

    @Test
    void paysAnOrderWhenTheGatewayApproves() {
        Order order = existingOrderOf("alice");
        when(orderRepository.findById(order.id())).thenReturn(Mono.just(order));
        when(paymentGateway.charge(any(PaymentRequest.class))).thenReturn(Mono.just(PaymentResult.ofApproved("PAY-1")));
        repositoryReturnsWhatItSaves();

        StepVerifier.create(service.pay(order.id(), ALICE))
                .assertNext(paid -> {
                    assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
                    assertThat(paid.paymentReference()).isEqualTo("PAY-1");
                })
                .verifyComplete();

        verify(eventPublisher).publish(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).singleElement().isInstanceOf(OrderPaid.class);
    }

    @Test
    void keepsTheOrderUnpaidWhenTheGatewayRejects() {
        Order order = existingOrderOf("alice");
        when(orderRepository.findById(order.id())).thenReturn(Mono.just(order));
        when(paymentGateway.charge(any())).thenReturn(Mono.just(PaymentResult.ofRejected("Insufficient funds")));

        StepVerifier.create(service.pay(order.id(), ALICE))
                .expectError(PaymentRejectedException.class)
                .verify();

        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void propagatesGatewayOutagesAsUnavailable() {
        Order order = existingOrderOf("alice");
        when(orderRepository.findById(order.id())).thenReturn(Mono.just(order));
        when(paymentGateway.charge(any()))
                .thenReturn(Mono.error(new PaymentGatewayUnavailableException("down", new RuntimeException())));

        StepVerifier.create(service.pay(order.id(), ALICE))
                .expectError(PaymentGatewayUnavailableException.class)
                .verify();
    }

    @Test
    void neverChargesAnOrderThatCannotBePaid() {
        Order order = existingOrderOf("alice");
        order.cancel("No longer needed", CLOCK);
        when(orderRepository.findById(order.id())).thenReturn(Mono.just(order));

        StepVerifier.create(service.pay(order.id(), ALICE))
                .expectError(InvalidOrderStateException.class)
                .verify();

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void customerCannotCancelSomeoneElsesOrder() {
        Order order = existingOrderOf("alice");
        when(orderRepository.findById(order.id())).thenReturn(Mono.just(order));

        StepVerifier.create(service.cancel(order.id(), BOB, "malicious"))
                .expectError(OrderNotFoundException.class)
                .verify();

        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
    }
}
