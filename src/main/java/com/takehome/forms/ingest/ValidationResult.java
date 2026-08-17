package com.takehome.forms.ingest;

import java.util.List;

public sealed interface ValidationResult {

	record Valid(IngestedForm form) implements ValidationResult {
	}

	record Invalid(List<String> errors) implements ValidationResult {
	}
}
