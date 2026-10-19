package com.cmanzano.orders.infrastructure.adapter.out.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.payment")
public record PaymentGatewayProperties(String baseUrl, Duration timeout) {
}
