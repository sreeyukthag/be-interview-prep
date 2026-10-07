package com.sreeyukthag.beinterviewprep.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreeyukthag.beinterviewprep.auth.entity.Role;
import com.sreeyukthag.beinterviewprep.auth.entity.User;
import com.sreeyukthag.beinterviewprep.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(
        properties = {"app.admin.email=Bootstrap-Admin@example.com", "app.admin.password=test-only-admin-password"})
class AdminBootstrapIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Test
    void startupCreatesTheConfiguredAdminWithAHashedPassword() {
        User admin = userRepository.findByEmail("bootstrap-admin@example.com").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("test-only-admin-password", admin.getPasswordHash()))
                .isTrue();
    }

    @Test
    void runningAgainLeavesTheExistingAdminUnchanged() {
        User before = userRepository.findByEmail("bootstrap-admin@example.com").orElseThrow();

        adminBootstrap.run(new DefaultApplicationArguments());

        User after = userRepository.findByEmail("bootstrap-admin@example.com").orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
    }
}
