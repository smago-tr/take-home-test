package com.takehome.forms;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FormsController {

	@PostMapping("/ingest")
	public Map<String, String> ingest() {
		return Map.of("message", "Ingesting form data");
	}
}
