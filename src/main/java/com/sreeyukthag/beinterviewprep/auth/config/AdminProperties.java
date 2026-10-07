package com.sreeyukthag.beinterviewprep.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("app.admin")
public record AdminProperties(String email, String password) {

    private static final int MIN_PASSWORD_LENGTH = 8;

    public AdminProperties {
        if (StringUtils.hasText(email) != StringUtils.hasText(password)) {
            throw new IllegalStateException("Set both ADMIN_EMAIL and ADMIN_PASSWORD, or neither");
        }
        if (StringUtils.hasText(password) && password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be at least %d characters".formatted(MIN_PASSWORD_LENGTH));
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(email);
    }

    @Override
    public String toString() {
        return "AdminProperties[email=%s, password=***]".formatted(email);
    }
}
