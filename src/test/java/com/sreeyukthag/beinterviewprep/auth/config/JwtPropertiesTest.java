package com.sreeyukthag.beinterviewprep.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static final Duration TTL = Duration.ofMinutes(15);

    @Test
    void secretShorterThan32BytesFailsAtStartup() {
        String shortSecret = "x".repeat(31);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtProperties(shortSecret, TTL));

        assertThat(ex.getMessage()).contains("JWT_SECRET").doesNotContain(shortSecret);
    }

    @Test
    void missingSecretFailsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtProperties(null, TTL));
    }

    @Test
    void nonPositiveTtlFailsAtStartup() {
        String secret = "x".repeat(32);

        assertThrows(IllegalStateException.class, () -> new JwtProperties(secret, Duration.ZERO));
    }

    @Test
    void toStringNeverPrintsTheSecret() {
        String secret = "s".repeat(40);

        String printed = new JwtProperties(secret, TTL).toString();

        assertThat(printed).doesNotContain(secret);
    }
}
