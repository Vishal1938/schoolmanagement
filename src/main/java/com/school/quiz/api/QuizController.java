package com.school.quiz.api;

import java.util.List;

import com.school.common.security.HasPermission;
import com.school.quiz.app.QuizService;
import com.school.quiz.domain.QuizStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quizzes as their authors manage them.
 *
 * <p>{@code @PreAuthorize} here only answers "may this caller use this endpoint at all", which keeps
 * students out entirely — they hold {@code QUIZ_ATTEMPT} and not {@code QUIZ_MANAGE}, and their own
 * endpoints are in {@link QuizAttemptController}. <em>Which</em> quizzes a caller sees and may change
 * is decided in {@link QuizService}: an admin gets all of them, a teacher only the ones they set.
 *
 * <p>Every response here may contain the answer key, which is exactly why nothing in this controller
 * is reachable with a student's permissions.
 */
@RestController
@RequestMapping("/quizzes")
@Tag(name = "Quizzes", description = "Setting quizzes, publishing them and reading the results")
public class QuizController {

	private final QuizService quizzes;

	public QuizController(QuizService quizzes) {
		this.quizzes = quizzes;
	}

	@GetMapping
	@PreAuthorize(HasPermission.QUIZ_MANAGE)
	@Operation(summary = "The quizzes you may manage",
			description = "An admin sees every quiz; a teacher sees the ones they set. Optionally narrowed by "
					+ "classId and status (DRAFT, PUBLISHED, CLOSED). Newest first. Questions are not "
					+ "included — use GET /quizzes/{id} for those.")
	public List<QuizSummaryResponse> list(
			@RequestParam(required = false) String classId,
			@RequestParam(required = false) QuizStatus status) {
		return quizzes.list(classId, status);
	}

	@GetMapping("/{id}")
	@PreAuthorize(HasPermission.QUIZ_MANAGE)
	@Operation(summary = "One quiz in full, with its answer key",
			description = "ADMIN for any quiz, a teacher only for their own (403 otherwise). Includes "
					+ "correctOptionIds and explanations, so this is never a student-facing response.")
	public QuizResponse get(@PathVariable String id) {
		return QuizResponse.of(quizzes.get(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.QUIZ_MANAGE)
	@Operation(summary = "Set a quiz",
			description = "Created as a DRAFT, which is the only status in which it can be edited. A draft may "
					+ "be incomplete — no questions, no answer key, no dates — because publishing is what "
					+ "checks for those. The class and subject must exist, the sections must belong to the "
					+ "class, and an empty sections array means every section. maxAttempts defaults to 1; "
					+ "question and option ids are generated when left out.")
	public QuizResponse create(@Valid @RequestBody QuizRequest request) {
		return QuizResponse.of(quizzes.create(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize(HasPermission.QUIZ_MANAGE)
	@Operation(summary = "Replace a draft quiz",
			description = "Only while the quiz is a DRAFT — 409 once it is published, because attempts refer to "
					+ "questions by id and score against a total. Replaces everything including the "
					+ "questions; send ids back to keep them. The author is never reassigned by an edit.")
	public QuizResponse update(@PathVariable String id, @Valid @RequestBody QuizRequest request) {
		return QuizResponse.of(quizzes.update(id, request));
	}

	@PostMapping("/{id}/publish")
	@PreAuthorize(HasPermission.QUIZ_MANAGE)
	@Operation(summary = "Publish a quiz",
			description = "DRAFT to PUBLISHED, after which it is frozen and visible to its class. Requires at "
					+ "least one question, a correct answer marked on every question, and startAt and endAt "
					+ "with endAt after startAt; every gap is reported at once as a 400 with field errors.")
	public QuizResponse publish(@PathVariable String id) {
		return QuizResponse.of(quizzes.publish(id));
	}

	@PostMapping("/{id}/close")
	@PreAuthorize(HasPermission.QUIZ_MANAGE)
	@Operation(summary = "Close a quiz",
			description = "PUBLISHED to CLOSED. No new attempts, whatever the window says, and it drops off the "
					+ "students' list. Attempts already in flight run to their own deadline. Results stay "
					+ "readable; there is no way back to PUBLISHED.")
	public QuizResponse close(@PathVariable String id) {
		return QuizResponse.of(quizzes.close(id));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(HasPermission.QUIZ_MANAGE)
	@Operation(summary = "Delete a draft quiz",
			description = "Only while the quiz is a DRAFT — 409 otherwise, because a published quiz may have "
					+ "been sat. Close a published quiz instead of deleting it.")
	public void delete(@PathVariable String id) {
		quizzes.delete(id);
	}

	@GetMapping("/{id}/results")
	@PreAuthorize(HasPermission.QUIZ_MANAGE)
	@Operation(summary = "The results sheet",
			description = "ADMIN for any quiz, a teacher only for their own. Built from the class roll, so it "
					+ "lists who did not attempt as well as who did: per student the best score and when it "
					+ "was submitted, and per question the percentage of submitted attempts that got it "
					+ "right. A blank answer counts as wrong.")
	public QuizResultsResponse results(@PathVariable String id) {
		return quizzes.results(id);
	}
}
