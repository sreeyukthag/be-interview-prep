package com.sreeyukthag.beinterviewprep.auth.config;

import com.sreeyukthag.beinterviewprep.auth.service.AuthService;
import com.sreeyukthag.beinterviewprep.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
class AdminBootstrap implements ApplicationRunner {

    private final AdminProperties properties;
    private final AuthService authService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            return;
        }
        try {
            authService.registerAdmin(properties.email(), properties.password());
            log.info("Bootstrap admin account created");
        } catch (ConflictException ex) {
            log.info("Bootstrap admin account already exists; leaving it unchanged");
        }
    }
}
