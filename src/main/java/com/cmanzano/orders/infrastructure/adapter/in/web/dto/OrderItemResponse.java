package com.cmanzano.orders.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

public record OrderItemResponse(String sku, String name, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
}
