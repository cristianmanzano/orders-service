package com.cmanzano.orders.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OrderLineRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,32}$", message = "must be an uppercase alphanumeric SKU") String sku,
        @Min(1) @Max(100) int quantity) {
}
