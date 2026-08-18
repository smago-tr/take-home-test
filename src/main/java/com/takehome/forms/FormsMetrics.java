package com.takehome.forms;

import com.takehome.forms.submission.EmailStatus;
import com.takehome.forms.submission.SubmissionRepository;
import com.takehome.forms.submission.SubmissionStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class FormsMetrics {

	private final MeterRegistry registry;
	private final Timer retrySweepTimer;

	public FormsMetrics(MeterRegistry registry, SubmissionRepository submissionRepository) {
		this.registry = registry;
		// Live count of submissions currently stuck in a non-terminal state — "how many forms
		// need a fix shipped right now," not just a point-in-time count from a single sweep.
		registry.gauge("forms.submissions.pending", submissionRepository,
				repo -> repo.findNeedingRetry().size());
		// publishPercentileHistogram() is required for Grafana's histogram_quantile() query to
		// have _bucket series to work with — the bare registry.timer(name) shortcut omits them.
		this.retrySweepTimer = Timer.builder("forms.retry.sweep.duration")
				.publishPercentileHistogram()
				.register(registry);
	}

	public void recordSubmissionOutcome(SubmissionStatus status) {
		registry.counter("forms.submissions.total", "status", status.name()).increment();
	}

	public void recordEmailOutcome(EmailStatus status) {
		registry.counter("forms.emails.total", "status", status.name()).increment();
	}

	public Timer.Sample startRetrySweepTimer() {
		return Timer.start(registry);
	}

	public void stopRetrySweepTimer(Timer.Sample sample) {
		sample.stop(retrySweepTimer);
	}
}
