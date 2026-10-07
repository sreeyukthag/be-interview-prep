package com.sreeyukthag.beinterviewprep.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt only uses the first 72 bytes, so a longer password would silently be truncated.
        @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String password) {

    @Override
    public String toString() {
        return "RegisterRequest[email=%s, password=***]".formatted(email);
    }
}
