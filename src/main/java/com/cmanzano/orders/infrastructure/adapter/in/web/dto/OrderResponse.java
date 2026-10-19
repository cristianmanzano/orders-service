package com.cmanzano.orders.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        String status,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String currency,
        String paymentReference,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt) {
}
