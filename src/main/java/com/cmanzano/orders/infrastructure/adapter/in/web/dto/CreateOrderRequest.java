package com.cmanzano.orders.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(@NotEmpty @Size(max = 50) List<@Valid OrderLineRequest> items) {
}
