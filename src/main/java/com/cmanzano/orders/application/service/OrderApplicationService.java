package com.cmanzano.orders.application.service;

import com.cmanzano.orders.application.port.in.CancelOrderUseCase;
import com.cmanzano.orders.application.port.in.CreateOrderUseCase;
import com.cmanzano.orders.application.port.in.GetOrderUseCase;
import com.cmanzano.orders.application.port.in.ListOrdersUseCase;
import com.cmanzano.orders.application.port.in.PayOrderUseCase;
import com.cmanzano.orders.application.port.in.Requester;
import com.cmanzano.orders.application.port.out.DomainEventPublisherPort;
import com.cmanzano.orders.application.port.out.OrderRepositoryPort;
import com.cmanzano.orders.application.port.out.PaymentGatewayPort;
import com.cmanzano.orders.application.port.out.PaymentGatewayPort.PaymentRequest;
import com.cmanzano.orders.application.port.out.PaymentGatewayPort.PaymentResult;
import com.cmanzano.orders.application.port.out.ProductCatalogPort;
import com.cmanzano.orders.domain.event.DomainEvent;
import com.cmanzano.orders.domain.exception.InvalidOrderException;
import com.cmanzano.orders.domain.exception.OrderNotFoundException;
import com.cmanzano.orders.domain.exception.PaymentRejectedException;
import com.cmanzano.orders.domain.model.CustomerId;
import com.cmanzano.orders.domain.model.Order;
import com.cmanzano.orders.domain.model.OrderId;
import com.cmanzano.orders.domain.model.OrderItem;
import com.cmanzano.orders.domain.model.Product;
import com.cmanzano.orders.domain.policy.DiscountPolicy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orquesta los casos de uso de pedidos. No contiene reglas de negocio (viven en el dominio)
 * ni detalles técnicos (viven en los adaptadores): solo coordina puertos.
 * <p>
 * No tiene anotaciones de Spring; se registra como bean en la capa de infraestructura.
 */
public class OrderApplicationService
        implements CreateOrderUseCase, GetOrderUseCase, ListOrdersUseCase, PayOrderUseCase, CancelOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ProductCatalogPort productCatalog;
    private final PaymentGatewayPort paymentGateway;
    private final DomainEventPublisherPort eventPublisher;
    private final DiscountPolicy discountPolicy;
    private final Clock clock;

    public OrderApplicationService(OrderRepositoryPort orderRepository,
                                   ProductCatalogPort productCatalog,
                                   PaymentGatewayPort paymentGateway,
                                   DomainEventPublisherPort eventPublisher,
                                   DiscountPolicy discountPolicy,
                                   Clock clock) {
        this.orderRepository = orderRepository;
        this.productCatalog = productCatalog;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.discountPolicy = discountPolicy;
        this.clock = clock;
    }

    @Override
    public Mono<Order> create(CreateOrderCommand command) {
        Set<String> skus = command.lines().stream().map(OrderLine::sku).collect(Collectors.toSet());
        if (skus.size() != command.lines().size()) {
            return Mono.error(new InvalidOrderException("Duplicated SKUs are not allowed in the same order"));
        }
        return productCatalog.findBySkus(skus)
                .collectMap(Product::sku, Function.identity())
                .map(catalog -> toItems(command.lines(), catalog))
                .map(items -> Order.create(new CustomerId(command.customerId()), items, discountPolicy, clock))
                .flatMap(this::persistAndPublish);
    }

    @Override
    public Mono<Order> getOrder(OrderId orderId, Requester requester) {
        return findAccessible(orderId, requester);
    }

    @Override
    public Flux<Order> listOrders(Requester requester) {
        return orderRepository.findByCustomerId(requester.asCustomerId());
    }

    @Override
    public Mono<Order> pay(OrderId orderId, Requester requester) {
        return findAccessible(orderId, requester)
                .doOnNext(Order::ensurePayable)
                .flatMap(order -> paymentGateway.charge(new PaymentRequest(order.id(), order.total()))
                        .map(result -> applyPayment(order, result)))
                .flatMap(this::persistAndPublish);
    }

    @Override
    public Mono<Order> cancel(OrderId orderId, Requester requester, String reason) {
        return findAccessible(orderId, requester)
                .map(order -> {
                    order.cancel(reason, clock);
                    return order;
                })
                .flatMap(this::persistAndPublish);
    }

    private static List<OrderItem> toItems(List<OrderLine> lines, Map<String, Product> catalog) {
        return lines.stream()
                .map(line -> Optional.ofNullable(catalog.get(line.sku()))
                        .map(product -> new OrderItem(product.sku(), product.name(), line.quantity(), product.price()))
                        .orElseThrow(() -> new InvalidOrderException("Unknown product: " + line.sku())))
                .toList();
    }

    private Order applyPayment(Order order, PaymentResult result) {
        if (!result.approved()) {
            throw new PaymentRejectedException(order.id(), result.reason());
        }
        order.markAsPaid(result.reference(), clock);
        return order;
    }

    /**
     * Si el pedido no existe o pertenece a otro cliente se responde igual ("no encontrado")
     * para no revelar la existencia de recursos ajenos (evita enumeración de IDs).
     */
    private Mono<Order> findAccessible(OrderId orderId, Requester requester) {
        return orderRepository.findById(orderId)
                .filter(requester::canAccess)
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(orderId)));
    }

    private Mono<Order> persistAndPublish(Order order) {
        List<DomainEvent> events = order.pullDomainEvents();
        return orderRepository.save(order)
                .flatMap(saved -> eventPublisher.publish(events).thenReturn(saved));
    }
}
