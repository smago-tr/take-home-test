package com.takehome.forms.submission;

import com.takehome.forms.AbstractIntegrationTest;
import com.takehome.forms.transform.TestTransformedForms;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class TransformedFormRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private TransformedFormRepository transformedFormRepository;

	@Test
	void insertsSuccessfullyForNewApplication() {
		Submission submission = submissionRepository.findOrCreate("session-1", "APP-1", "{}");

		boolean inserted = transformedFormRepository.insertUnlessApplicationAlreadyTransformed(
				submission.id(), TestTransformedForms.withSessionAndApplication("session-1", "APP-1"));

		assertThat(inserted).isTrue();
	}

	@Test
	void rejectsSecondInsertForSameApplicationReferenceUnderDifferentSession() {
		Submission subA = submissionRepository.findOrCreate("session-A", "APP-SAME", "{}");
		Submission subB = submissionRepository.findOrCreate("session-B", "APP-SAME", "{}");

		boolean first = transformedFormRepository.insertUnlessApplicationAlreadyTransformed(
				subA.id(), TestTransformedForms.withSessionAndApplication("session-A", "APP-SAME"));
		boolean second = transformedFormRepository.insertUnlessApplicationAlreadyTransformed(
				subB.id(), TestTransformedForms.withSessionAndApplication("session-B", "APP-SAME"));

		assertThat(first).isTrue();
		assertThat(second).isFalse();
	}

	@Test
	void allowsDifferentApplicationsBothToInsert() {
		Submission subA = submissionRepository.findOrCreate("session-A", "APP-ONE", "{}");
		Submission subB = submissionRepository.findOrCreate("session-B", "APP-TWO", "{}");

		boolean first = transformedFormRepository.insertUnlessApplicationAlreadyTransformed(
				subA.id(), TestTransformedForms.withSessionAndApplication("session-A", "APP-ONE"));
		boolean second = transformedFormRepository.insertUnlessApplicationAlreadyTransformed(
				subB.id(), TestTransformedForms.withSessionAndApplication("session-B", "APP-TWO"));

		assertThat(first).isTrue();
		assertThat(second).isTrue();
	}
}
