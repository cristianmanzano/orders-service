package com.cmanzano.orders.infrastructure.adapter.in.web;

import com.cmanzano.orders.infrastructure.adapter.in.web.dto.TokenRequest;
import com.cmanzano.orders.infrastructure.adapter.in.web.dto.TokenResponse;
import com.cmanzano.orders.infrastructure.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;

    public AuthController(ReactiveAuthenticationManager authenticationManager, JwtTokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @PostMapping("/token")
    @Operation(summary = "Intercambia usuario/contraseña por un access token JWT")
    public Mono<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
        var credentials = UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password());
        return authenticationManager.authenticate(credentials)
                .map(tokenService::issue)
                .map(token -> new TokenResponse(token.value(), "Bearer", token.expiresInSeconds()));
    }
}
