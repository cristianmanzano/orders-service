package com.cmanzano.orders.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.secret());
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must be at least 256 bits long");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /**
     * Valida firma HS256, expiración (exp/nbf) y emisor (iss) de cada token entrante.
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    ReactiveUserDetailsService userDetailsService(UserAccountsProperties properties, PasswordEncoder passwordEncoder) {
        List<UserDetails> users = properties.users().stream()
                .map(account -> User.withUsername(account.username())
                        .password(passwordEncoder.encode(account.password()))
                        .roles(account.roles().toArray(String[]::new))
                        .build())
                .toList();
        return users.isEmpty() ? username -> Mono.empty() : new MapReactiveUserDetailsService(users);
    }

    @Bean
    ReactiveAuthenticationManager loginAuthenticationManager(ReactiveUserDetailsService userDetailsService,
                                                             PasswordEncoder passwordEncoder) {
        var manager = new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        manager.setPasswordEncoder(passwordEncoder);
        return manager;
    }
}
