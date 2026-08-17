package com.takehome.forms;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class FormsController {

	private final IngestionService ingestionService;

	public FormsController(IngestionService ingestionService) {
		this.ingestionService = ingestionService;
	}

	@PostMapping("/ingest")
	public ResponseEntity<Map<String, Object>> ingest(@RequestBody JsonNode payload) {
		IngestOutcome outcome = ingestionService.ingest(payload);

		if (outcome instanceof IngestOutcome.MalformedRequest malformed) {
			return ResponseEntity.badRequest().body(Map.of("error", malformed.reason()));
		}

		IngestOutcome.Processed processed = (IngestOutcome.Processed) outcome;
		HttpStatus httpStatus = switch (processed.status()) {
			case READY, DUPLICATE_APPLICATION -> HttpStatus.OK;
			case SCHEMA_INVALID, TRANSFORM_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
			case GEOCODE_FAILED -> HttpStatus.BAD_GATEWAY;
			case RECEIVED -> HttpStatus.INTERNAL_SERVER_ERROR; // process() never returns this; a bug if it does
		};

		Map<String, Object> body = new HashMap<>();
		body.put("status", processed.status());
		if (processed.error() != null) {
			body.put("error", processed.error());
		}

		return ResponseEntity.status(httpStatus).body(body);
	}
}
