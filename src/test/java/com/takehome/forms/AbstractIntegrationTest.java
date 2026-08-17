package com.takehome.forms;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

// Connects to the docker-compose Postgres (`docker compose up -d`) rather than Testcontainers —
// dynamically-published container ports weren't reliably reachable on this Docker setup.
// Each test gets a clean slate via resetDatabase() instead of a fresh container per run.
@SpringBootTest
public abstract class AbstractIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void resetDatabase() {
		List<String> tables = jdbcTemplate.queryForList(
				"SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename != 'flyway_schema_history'",
				String.class
		);
		if (!tables.isEmpty()) {
			jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
		}
	}
}
