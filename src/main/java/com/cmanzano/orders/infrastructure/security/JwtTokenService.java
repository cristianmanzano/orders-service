package com.cmanzano.orders.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenService {

    public static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Emite un access token de vida corta firmado con HS256. Solo incluye los claims necesarios
     * (sin datos sensibles: el payload de un JWT es legible por cualquiera).
     */
    public IssuedToken issue(Authentication authentication) {
        Instant now = clock.instant();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith(ROLE_PREFIX) ? authority.substring(ROLE_PREFIX.length()) : authority)
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .claim(ROLES_CLAIM, roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, properties.ttl().toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
