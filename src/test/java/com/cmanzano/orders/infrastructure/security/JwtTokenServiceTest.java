package com.cmanzano.orders.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "wgfkAHournwmnA0/3HLG5/ZJklwppoU8LZrVOlxR5oQyH8y2v9fjRYxPrtlLAL7/";
    private static final String OTHER_SECRET = "DJE1H+wRnOWVxGdo+fokConPPfUWBNanrW7ZH2CA3ppERMEFVk3Qh7DgCtUT5mem";

    private final JwtConfig config = new JwtConfig();
    private final JwtProperties properties = new JwtProperties(SECRET, "orders-service", Duration.ofMinutes(15));

    private final UsernamePasswordAuthenticationToken admin = UsernamePasswordAuthenticationToken.authenticated(
            "admin", null, List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")));

    private JwtTokenService tokenServiceWith(JwtProperties props, Clock clock) {
        return new JwtTokenService(config.jwtEncoder(config.jwtSigningKey(props)), props, clock);
    }

    private ReactiveJwtDecoder decoderFor(JwtProperties props) {
        return config.jwtDecoder(config.jwtSigningKey(props), props);
    }

    @Test
    void issuedTokensCarrySubjectIssuerAndRoles() {
        var token = tokenServiceWith(properties, Clock.systemUTC()).issue(admin);

        assertThat(token.expiresInSeconds()).isEqualTo(900);
        StepVerifier.create(decoderFor(properties).decode(token.value()))
                .assertNext(jwt -> {
                    assertThat(jwt.getSubject()).isEqualTo("admin");
                    assertThat(jwt.getClaimAsString("iss")).isEqualTo("orders-service");
                    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER", "ADMIN");
                })
                .verifyComplete();
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() {
        var forged = tokenServiceWith(new JwtProperties(OTHER_SECRET, "orders-service", Duration.ofMinutes(15)),
                Clock.systemUTC()).issue(admin);

        StepVerifier.create(decoderFor(properties).decode(forged.value()))
                .expectError(BadJwtException.class)
                .verify();
    }

    @Test
    void rejectsExpiredTokens() {
        Clock anHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        var expired = tokenServiceWith(properties, anHourAgo).issue(admin);

        StepVerifier.create(decoderFor(properties).decode(expired.value()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(JwtValidationException.class)
                        .hasMessageContaining("expired"))
                .verify();
    }

    @Test
    void refusesWeakSecrets() {
        var weak = new JwtProperties("c2hvcnQ=", "orders-service", Duration.ofMinutes(15));

        assertThatThrownBy(() -> config.jwtSigningKey(weak)).isInstanceOf(IllegalStateException.class);
    }
}
