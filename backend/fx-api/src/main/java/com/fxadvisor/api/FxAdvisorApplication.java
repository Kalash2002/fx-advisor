package com.fxadvisor.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Disable Flyway autoconfigure for Sprint 1 — we'll wire it properly in Sprint 2
//@SpringBootApplication(exclude = {FlywayAutoConfiguration.class})
// INTERVIEW: What does @SpringBootApplication do?
// It's a convenience annotation combining three annotations:
// @Configuration — marks this class as a source of @Bean definitions
// @EnableAutoConfiguration — Spring Boot scans classpath and auto-configures
//   beans (e.g., sees mysql-connector-j → configures DataSource automatically)
// @ComponentScan — scans the current package and sub-packages for
//   @Component, @Service, @Repository, @Controller beans
//
// Sprint 1 excluded FlywayAutoConfiguration to prevent startup failure
// (tables didn't exist yet). Sprint 2 removes that exclusion — Flyway
// now runs and creates all tables on first startup.
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.fxadvisor.api",
        "com.fxadvisor.auth",
        "com.fxadvisor.rate",
        "com.fxadvisor.compliance",
        "com.fxadvisor.corridor",
        "com.fxadvisor.session",
        "com.fxadvisor.audit",
        "com.fxadvisor.observability",
        "com.fxadvisor.user"
})
@EnableJpaRepositories(basePackages = "com.fxadvisor")
@EntityScan(basePackages = "com.fxadvisor")
public class FxAdvisorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FxAdvisorApplication.class, args);
    }
}