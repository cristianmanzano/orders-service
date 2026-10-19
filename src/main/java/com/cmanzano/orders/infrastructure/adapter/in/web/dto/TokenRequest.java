package com.cmanzano.orders.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) {

    /**
     * Evita que la contraseña termine en logs si el objeto se imprime.
     */
    @Override
    public String toString() {
        return "TokenRequest[username=" + username + ", password=****]";
    }
}
