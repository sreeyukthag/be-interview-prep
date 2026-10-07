package com.sreeyukthag.beinterviewprep.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AdminPropertiesTest {

    @Test
    void neitherValueSetMeansNoBootstrap() {
        AdminProperties properties = new AdminProperties("", "");

        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void onlyOneValueSetFailsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new AdminProperties("admin@example.com", ""));
    }

    @Test
    void shortPasswordFailsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new AdminProperties("admin@example.com", "short"));
    }
}
