package com.apparel.tracking;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

/**
 * Boots the full context against a throwaway Postgres and runs every Flyway
 * migration. Skipped automatically on machines without a Docker daemon.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ApparelTrackingApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @Test
    void contextLoads() {
    }
}
