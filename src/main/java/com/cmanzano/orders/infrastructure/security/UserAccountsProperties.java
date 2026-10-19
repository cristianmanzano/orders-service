package com.cmanzano.orders.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Usuarios de demostración. En un entorno real la autenticación se delega a un IdP
 * (Cognito, Keycloak, Entra ID) y este servicio solo actuaría como resource server.
 */
@ConfigurationProperties("app.security")
public record UserAccountsProperties(List<UserAccount> users) {

    public UserAccountsProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public record UserAccount(String username, String password, List<String> roles) {
    }
}
