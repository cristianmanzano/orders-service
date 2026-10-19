package com.cmanzano.orders.infrastructure.adapter.in.web;

import com.cmanzano.orders.application.port.in.CancelOrderUseCase;
import com.cmanzano.orders.application.port.in.CreateOrderUseCase;
import com.cmanzano.orders.application.port.in.GetOrderAuditUseCase;
import com.cmanzano.orders.application.port.in.GetOrderUseCase;
import com.cmanzano.orders.application.port.in.ListOrdersUseCase;
import com.cmanzano.orders.application.port.in.PayOrderUseCase;
import com.cmanzano.orders.domain.model.OrderId;
import com.cmanzano.orders.infrastructure.adapter.in.web.dto.AuditEntryResponse;
import com.cmanzano.orders.infrastructure.adapter.in.web.dto.CancelOrderRequest;
import com.cmanzano.orders.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.cmanzano.orders.infrastructure.adapter.in.web.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

/**
 * Adaptador de entrada HTTP. Solo depende de los puertos de entrada (casos de uso),
 * nunca de los servicios concretos ni de los adaptadores de salida.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final CreateOrderUseCase createOrder;
    private final GetOrderUseCase getOrder;
    private final ListOrdersUseCase listOrders;
    private final PayOrderUseCase payOrder;
    private final CancelOrderUseCase cancelOrder;
    private final GetOrderAuditUseCase getOrderAudit;

    public OrderController(CreateOrderUseCase createOrder, GetOrderUseCase getOrder, ListOrdersUseCase listOrders,
                           PayOrderUseCase payOrder, CancelOrderUseCase cancelOrder,
                           GetOrderAuditUseCase getOrderAudit) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
        this.listOrders = listOrders;
        this.payOrder = payOrder;
        this.cancelOrder = cancelOrder;
        this.getOrderAudit = getOrderAudit;
    }

    @PostMapping
    @Operation(summary = "Crea un pedido para el usuario autenticado")
    public Mono<ResponseEntity<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        var command = OrderWebMapper.toCommand(request, OrderWebMapper.toRequester(jwt));
        return createOrder.create(command)
                .map(OrderWebMapper::toResponse)
                .map(body -> ResponseEntity.created(URI.create("/api/v1/orders/" + body.id())).body(body));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Consulta un pedido propio (o cualquiera si es ADMIN)")
    public Mono<OrderResponse> get(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
        return getOrder.getOrder(new OrderId(orderId), OrderWebMapper.toRequester(jwt))
                .map(OrderWebMapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "Lista los pedidos del usuario autenticado")
    public Flux<OrderResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return listOrders.listOrders(OrderWebMapper.toRequester(jwt)).map(OrderWebMapper::toResponse);
    }

    @PostMapping("/{orderId}/payment")
    @Operation(summary = "Paga un pedido a través de la pasarela externa")
    public Mono<OrderResponse> pay(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
        return payOrder.pay(new OrderId(orderId), OrderWebMapper.toRequester(jwt))
                .map(OrderWebMapper::toResponse);
    }

    @PostMapping("/{orderId}/cancellation")
    @Operation(summary = "Cancela un pedido que aún no ha sido pagado")
    public Mono<OrderResponse> cancel(@PathVariable UUID orderId, @Valid @RequestBody CancelOrderRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return cancelOrder.cancel(new OrderId(orderId), OrderWebMapper.toRequester(jwt), request.reason())
                .map(OrderWebMapper::toResponse);
    }

    @GetMapping("/{orderId}/audit")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Traza de auditoría del pedido (DynamoDB). Solo ADMIN")
    public Flux<AuditEntryResponse> audit(@PathVariable UUID orderId) {
        return getOrderAudit.getAuditTrail(new OrderId(orderId)).map(OrderWebMapper::toResponse);
    }
}
