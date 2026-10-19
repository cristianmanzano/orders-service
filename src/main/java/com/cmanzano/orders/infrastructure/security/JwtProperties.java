package com.cmanzano.orders.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret clave HMAC en Base64 (mínimo 256 bits). Se inyecta por variable de entorno / Secret,
 *               nunca se versiona un valor productivo.
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration ttl) {
}
