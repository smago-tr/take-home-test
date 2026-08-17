package com.takehome.forms;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for tests that need a real Postgres instance (per "we expect you to use an actual
 * database"). Spring Boot wires the container's JDBC connection details in automatically via
 * @ServiceConnection — Flyway migrations run against it on context startup just like they would
 * against the docker-compose Postgres locally.
 *
 * Extend this from any test that hits the database; Testcontainers reuses one container across
 * subclasses within a JVM run.
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
}
