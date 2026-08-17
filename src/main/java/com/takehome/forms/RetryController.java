package com.takehome.forms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RetryController {

	private final IngestionService ingestionService;
	private final String apiKey;

	public RetryController(IngestionService ingestionService, @Value("${retry.api-key}") String apiKey) {
		this.ingestionService = ingestionService;
		this.apiKey = apiKey;
	}

	@PostMapping("/retry")
	public ResponseEntity<Map<String, Object>> retry(@RequestHeader(value = "X-API-Key", required = false) String providedKey) {
		if (providedKey == null || !providedKey.equals(apiKey)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		RetrySummary summary = ingestionService.retryAll();
		return ResponseEntity.ok(Map.of(
				"submissionsRetried", summary.submissionsRetried(),
				"emailsRetried", summary.emailsRetried()
		));
	}
}
