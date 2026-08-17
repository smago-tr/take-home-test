package com.takehome.forms.submission;

public enum SubmissionStatus {
	RECEIVED,
	SCHEMA_INVALID,
	GEOCODE_FAILED,
	TRANSFORM_FAILED,
	READY,
	/**
	 * Terminal like READY, but this submission never gets its own transformed_forms row —
	 * its application_reference was already transformed under a different session_id.
	 */
	DUPLICATE_APPLICATION
}
