package com.cmanzano.orders.infrastructure.adapter.in.web;

import com.cmanzano.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.cmanzano.orders.application.port.in.CreateOrderUseCase.OrderLine;
import com.cmanzano.orders.application.port.in.Requester;
import com.cmanzano.orders.domain.model.Order;
import com.cmanzano.orders.domain.model.OrderAuditEntry;
import com.cmanzano.orders.domain.model.OrderItem;
import com.cmanzano.orders.infrastructure.adapter.in.web.dto.AuditEntryResponse;
import com.cmanzano.orders.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.cmanzano.orders.infrastructure.adapter.in.web.dto.OrderItemResponse;
import com.cmanzano.orders.infrastructure.adapter.in.web.dto.OrderResponse;
import com.cmanzano.orders.infrastructure.security.JwtTokenService;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Traduce entre el contrato HTTP (DTOs) y el modelo de la aplicación. El dominio nunca se
 * serializa directamente: así la API puede evolucionar sin acoplarse a las entidades.
 */
final class OrderWebMapper {

    private OrderWebMapper() {
    }

    static CreateOrderCommand toCommand(CreateOrderRequest request, Requester requester) {
        List<OrderLine> lines = request.items().stream()
                .map(item -> new OrderLine(item.sku(), item.quantity()))
                .toList();
        return new CreateOrderCommand(requester.customerId(), lines);
    }

    static Requester toRequester(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtTokenService.ROLES_CLAIM);
        return new Requester(jwt.getSubject(), roles != null && roles.contains("ADMIN"));
    }

    static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.id().value(),
                order.customerId().value(),
                order.status().name(),
                order.items().stream().map(OrderWebMapper::toItemResponse).toList(),
                order.subtotal().amount(),
                order.discount().amount(),
                order.total().amount(),
                order.total().currency(),
                order.paymentReference(),
                order.cancellationReason(),
                order.createdAt(),
                order.updatedAt());
    }

    static AuditEntryResponse toResponse(OrderAuditEntry entry) {
        return new AuditEntryResponse(entry.eventId(), entry.eventType(), entry.detail(), entry.occurredAt());
    }

    private static OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(item.sku(), item.name(), item.quantity(), item.unitPrice().amount(),
                item.subtotal().amount());
    }
}
