package com.takehome.forms;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Base class for tests that need a real Postgres instance (per "we expect you to use an actual
 * database"). Connects to the docker-compose Postgres directly (run `docker compose up -d`
 * first) rather than a Testcontainers-managed instance: on this machine's local Docker setup
 * (Rancher Desktop), freshly-created ephemeral containers' dynamically-published ports were not
 * reliably reachable from the host — even Testcontainers' own Ryuk sidecar container failed to
 * connect — so the persistent docker-compose container is used instead. Still a real Postgres,
 * just not spun up fresh per test run; each test gets a clean slate via resetDatabase() instead.
 */
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
