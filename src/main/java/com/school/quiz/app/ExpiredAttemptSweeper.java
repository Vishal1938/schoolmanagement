package com.school.quiz.app;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.school.quiz.domain.QuizAttempt;
import com.school.quiz.infra.QuizAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Writes off attempts whose deadline passed with nothing submitted.
 *
 * <p>A student who starts a quiz and walks away leaves an attempt in flight for ever. That matters
 * for two reasons, and neither is cosmetic: the attempt would stay <em>resumable</em>, letting them
 * come back days later and sit a quiz whose window has closed, and it would never appear on the
 * results sheet, so a teacher could not tell "did not attempt" from "started and gave up".
 *
 * <p>So the attempt is submitted on their behalf, with no answers and a score of zero. It counts
 * against {@code maxAttempts} — the paper was taken away and read — and {@code autoSubmitted} marks
 * it, so a zero nobody earned is distinguishable from a zero somebody did.
 *
 * <p>The sweep is late by design. It waits out the same 30-second grace that
 * {@link QuizAttemptService#SUBMIT_GRACE} gives a submission, so a student whose answers are in
 * flight at the deadline cannot have their paper written off underneath them.
 */
@Component
public class ExpiredAttemptSweeper {

	private static final Logger log = LoggerFactory.getLogger(ExpiredAttemptSweeper.class);

	private final QuizAttemptRepository attempts;
	private final Clock clock;

	public ExpiredAttemptSweeper(QuizAttemptRepository attempts, Clock clock) {
		this.attempts = attempts;
		this.clock = clock;
	}

	/**
	 * Every minute, because a minute is roughly how long a student will accept being told "submitting"
	 * before they decide the app is broken.
	 *
	 * <p>{@code fixedDelay} rather than {@code fixedRate}, so a slow pass over a backlog does not have
	 * the next one starting on top of it, and one attempt's failure does not stop the pass — each is
	 * saved on its own and a failure is logged and stepped over, with the next run trying again.
	 *
	 * <p>{@code submittedAt} is set to the deadline rather than to now: that is the moment the attempt
	 * actually ended, and it keeps the record honest about the clock regardless of when this job
	 * happened to run.
	 */
	@Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void sweepExpiredAttempts() {
		Instant cutoff = Instant.now(clock).minus(QuizAttemptService.SUBMIT_GRACE);
		List<QuizAttempt> expired = attempts
				.findTop200BySubmittedAtIsNullAndDeadlineLessThanOrderByDeadlineAsc(cutoff);
		if (expired.isEmpty()) {
			return;
		}

		int swept = 0;
		for (QuizAttempt attempt : expired) {
			try {
				attempts.save(attempt.toBuilder()
						.answers(List.of())
						.score(0)
						.submittedAt(attempt.getDeadline())
						.autoSubmitted(true)
						.build());
				swept++;
			}
			catch (RuntimeException ex) {
				log.warn("Could not auto-submit expired quiz attempt {}: {}", attempt.getId(), ex.getMessage());
			}
		}
		log.info("Auto-submitted {} of {} expired quiz attempt(s)", swept, expired.size());
	}
}
