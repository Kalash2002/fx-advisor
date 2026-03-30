package com.fxadvisor.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
// Disable Flyway autoconfigure for Sprint 1 — we'll wire it properly in Sprint 2
@SpringBootApplication(exclude = {FlywayAutoConfiguration.class})
public class FxAdvisorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FxAdvisorApplication.class, args);
    }
}