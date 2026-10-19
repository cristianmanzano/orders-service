package com.cmanzano.orders.infrastructure.adapter.in.web.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
