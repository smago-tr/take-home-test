package com.takehome.forms.transform;

public sealed interface TransformResult {

	record Success(TransformedForm form) implements TransformResult {
	}

	record Failure(String reason) implements TransformResult {
	}
}
