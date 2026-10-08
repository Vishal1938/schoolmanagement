package com.school.quiz.api;

import java.util.List;

import com.school.common.security.HasPermission;
import com.school.quiz.app.QuizAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quizzes as a student sits them.
 *
 * <p>Everything here needs {@code QUIZ_ATTEMPT}, which only a student holds — so a teacher cannot
 * start an attempt and an admin cannot submit one on somebody's behalf. Which quizzes a student sees,
 * and whose attempt they may submit, is decided in {@link QuizAttemptService} from their own
 * enrollment and login, never from anything in the request.
 *
 * <p>Mapped under the same {@code /quizzes} root as {@link QuizController} because that is the path
 * the contract gives, but kept as a separate controller: these responses must never carry an answer
 * key, and that is easier to keep true when the two sets of endpoints do not sit in one file.
 */
@RestController
@RequestMapping("/quizzes")
@Tag(name = "Quiz attempts", description = "Sitting a quiz: what is available, starting, submitting and history")
public class QuizAttemptController {

	private final QuizAttemptService attempts;

	public QuizAttemptController(QuizAttemptService attempts) {
		this.attempts = attempts;
	}

	@GetMapping("/available")
	@PreAuthorize(HasPermission.QUIZ_ATTEMPT)
	@Operation(summary = "Quizzes you can sit",
			description = "The published quizzes for your own class and section, soonest first. Each carries "
					+ "status (UPCOMING, OPEN or ENDED), attemptsUsed, bestScore, and whether an attempt is "
					+ "already in progress. No questions — those come from starting an attempt.")
	public List<AvailableQuizResponse> available() {
		return attempts.available();
	}

	@PostMapping("/{id}/attempts")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.QUIZ_ATTEMPT)
	@Operation(summary = "Start an attempt",
			description = "Only while the quiz is OPEN and you have attempts left. Returns the questions "
					+ "**without correctOptionIds or explanations**, shuffled if the quiz says so, plus the "
					+ "deadline — the earlier of the time limit and the quiz's endAt. If an attempt of yours "
					+ "is still in flight within its deadline, that one is returned instead (`resumed: "
					+ "true`), so a refresh does not cost you an attempt or reset the clock.")
	public AttemptStartResponse start(@PathVariable String id) {
		return attempts.start(id);
	}

	@PutMapping("/attempts/{attemptId}")
	@PreAuthorize(HasPermission.QUIZ_ATTEMPT)
	@Operation(summary = "Submit an attempt",
			description = "Your own attempt only. Rejected if it was already submitted (409) or if more than "
					+ "30 seconds have passed since its deadline (422). Graded on the server: full marks for "
					+ "an exact match, zero otherwise, with MCQ_MULTI all-or-nothing. Questions left out "
					+ "score zero. Returns score and maxScore, plus a per-question review with the correct "
					+ "answers and explanations only if the quiz has showAnswersAfterSubmit.")
	public AttemptResultResponse submit(@PathVariable String attemptId,
			@Valid @RequestBody SubmitAttemptRequest request) {
		return attempts.submit(attemptId, request);
	}

	@GetMapping("/attempts/mine")
	@PreAuthorize(HasPermission.QUIZ_ATTEMPT)
	@Operation(summary = "Your past attempts",
			description = "Newest first, with score and maxScore. `autoSubmitted` marks an attempt whose "
					+ "deadline passed with nothing submitted: it scored zero and still counts as used.")
	public List<MyAttemptResponse> mine() {
		return attempts.mine();
	}
}
