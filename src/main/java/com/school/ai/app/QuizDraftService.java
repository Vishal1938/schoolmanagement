package com.school.ai.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.school.academics.app.SchoolClassService;
import com.school.academics.app.SubjectService;
import com.school.academics.domain.SchoolClass;
import com.school.academics.domain.Subject;
import com.school.ai.api.QuizDraftRequest;
import com.school.ai.api.QuizDraftResponse;
import com.school.ai.infra.AiGateway;
import com.school.quiz.domain.QuestionType;
import org.springframework.stereotype.Service;

/**
 * Drafting quiz questions on a topic.
 *
 * <p><strong>Nothing is saved.</strong> The endpoint returns questions in the shape
 * {@code POST /quizzes} takes and stops there; a draft becomes a quiz when a teacher saves it, and
 * not before.
 *
 * <p><strong>Every question the model returns is checked, and a bad one is dropped rather than
 * repaired.</strong> The rule is simply "would {@code POST /quizzes} reject this?" — no key, a key
 * pointing at an option that is not there, one option, eleven options, a true/false with three
 * choices, text past the limit. Patching a half-written question would mean guessing at what it was
 * supposed to ask, and a teacher is better served by nine good questions and a count of what went
 * missing than by ten of which one is quietly wrong.
 */
@Service
public class QuizDraftService {

	private static final String SYSTEM = """
			You write quiz questions for a school teacher in India.

			Rules:
			- Write for the class and subject you are given. Use the vocabulary of that year group.
			- Every question must be answerable from the topic alone. No trick questions, no questions \
			about the textbook's page numbers or edition, nothing that needs a diagram.
			- Options must be plausible. A wrong option should be a mistake a student could actually \
			make, not filler.
			- Exactly one option is correct for MCQ_SINGLE and TRUE_FALSE. Two or more are correct for \
			MCQ_MULTI. TRUE_FALSE has exactly two options.
			- correctOptions holds the 1-based positions of the correct options in the options list.
			- Write one short explanation per question, addressed to the student, saying why the answer \
			is right.
			- Plain text only: no markdown, no numbering, no "Question 1:" prefix.
			""";

	private final AiFeature feature;
	private final AiGateway ai;
	private final SubjectService subjects;
	private final SchoolClassService classes;

	public QuizDraftService(AiFeature feature, AiGateway ai, SubjectService subjects, SchoolClassService classes) {
		this.feature = feature;
		this.ai = ai;
		this.subjects = subjects;
		this.classes = classes;
	}

	public QuizDraftResponse draft(QuizDraftRequest request) {
		feature.require();

		// 404 before the provider is paid anything, and the names are what the prompt needs anyway.
		Subject subject = subjects.get(request.subjectId());
		SchoolClass schoolClass = classes.get(request.classId());

		Draft draft = ai.entity("draft quiz questions", SYSTEM, userPrompt(request, subject, schoolClass),
				Draft.class);

		List<DraftQuestion> offered = draft.questions() == null ? List.of() : draft.questions();
		List<QuizDraftResponse.Question> kept = new ArrayList<>();
		int dropped = 0;
		for (DraftQuestion question : offered) {
			if (kept.size() == request.count()) {
				// Past what was asked for: not a fault, so not counted as dropped either.
				break;
			}
			QuizDraftResponse.Question converted = convert(question, kept.size() + 1);
			if (converted == null) {
				dropped++;
			}
			else {
				kept.add(converted);
			}
		}

		return new QuizDraftResponse(subject.getId(), subject.getName(), schoolClass.getId(), schoolClass.getName(),
				request.topic(), request.difficulty(), request.count(), dropped, kept);
	}

	// --- the prompt -------------------------------------------------------------------------------

	private static String userPrompt(QuizDraftRequest request, Subject subject, SchoolClass schoolClass) {
		Map<QuestionType, Integer> mix = scaledMix(request);
		StringBuilder prompt = new StringBuilder()
				.append("Class: ").append(schoolClass.getName()).append('\n')
				.append("Subject: ").append(subject.getName()).append('\n')
				.append("Topic: ").append(request.topic()).append('\n')
				.append("Difficulty: ").append(request.difficulty()).append('\n')
				.append("Write exactly ").append(request.count()).append(" questions, made up of:\n");
		mix.forEach((type, howMany) -> {
			if (howMany > 0) {
				prompt.append("- ").append(howMany).append(' ').append(type).append('\n');
			}
		});
		return prompt.toString();
	}

	/**
	 * The requested mix, scaled so the counts add up to {@code count}.
	 *
	 * <p>Largest remainder, with the rounding slack given to whichever type was asked for most, so a
	 * mix of 1:1:1 over four questions comes out 2:1:1 rather than 1:1:1 and a model left to pick the
	 * fourth. No mix, or a mix of nothing, means all single-answer.
	 */
	private static Map<QuestionType, Integer> scaledMix(QuizDraftRequest request) {
		QuizDraftRequest.TypeMix mix = request.typeMix();
		int single = mix == null ? 0 : orZero(mix.mcqSingle());
		int multi = mix == null ? 0 : orZero(mix.mcqMulti());
		int trueFalse = mix == null ? 0 : orZero(mix.trueFalse());
		int weight = single + multi + trueFalse;
		if (weight == 0) {
			return Map.of(QuestionType.MCQ_SINGLE, request.count());
		}
		// Ordered, so the same request always produces the same prompt.
		Map<QuestionType, Integer> scaled = new LinkedHashMap<>();

		int count = request.count();
		int scaledSingle = single * count / weight;
		int scaledMulti = multi * count / weight;
		int scaledTrueFalse = trueFalse * count / weight;
		int slack = count - scaledSingle - scaledMulti - scaledTrueFalse;
		if (multi >= single && multi >= trueFalse) {
			scaledMulti += slack;
		}
		else if (trueFalse >= single) {
			scaledTrueFalse += slack;
		}
		else {
			scaledSingle += slack;
		}
		scaled.put(QuestionType.MCQ_SINGLE, scaledSingle);
		scaled.put(QuestionType.MCQ_MULTI, scaledMulti);
		scaled.put(QuestionType.TRUE_FALSE, scaledTrueFalse);
		return scaled;
	}

	private static int orZero(Integer value) {
		return value == null ? 0 : value;
	}

	// --- validation -------------------------------------------------------------------------------

	/** The converted question, or null when it is one {@code POST /quizzes} would refuse. */
	private static QuizDraftResponse.Question convert(DraftQuestion question, int position) {
		if (question == null || question.type() == null || isBlank(question.text())
				|| question.text().length() > 2_000) {
			return null;
		}
		List<String> options = question.options() == null ? List.of() : question.options();
		if (options.size() < 2 || options.size() > 10
				|| options.stream().anyMatch(option -> isBlank(option) || option.length() > 500)) {
			return null;
		}
		if (question.type() == QuestionType.TRUE_FALSE && options.size() != 2) {
			return null;
		}

		// A set, so a model naming the same option twice is read as naming it once rather than as
		// keying a two-answer question.
		LinkedHashSet<Integer> correct = new LinkedHashSet<>(
				question.correctOptions() == null ? List.of() : question.correctOptions());
		if (correct.isEmpty() || correct.stream().anyMatch(index -> index == null || index < 1
				|| index > options.size())) {
			return null;
		}
		boolean singleAnswer = question.type() == QuestionType.MCQ_SINGLE
				|| question.type() == QuestionType.TRUE_FALSE;
		if (singleAnswer && correct.size() != 1) {
			return null;
		}
		if (question.explanation() != null && question.explanation().length() > 2_000) {
			return null;
		}

		String questionId = "q" + position;
		List<QuizDraftResponse.Option> converted = new ArrayList<>();
		for (int index = 0; index < options.size(); index++) {
			converted.add(new QuizDraftResponse.Option(optionId(questionId, index + 1), options.get(index)));
		}
		return new QuizDraftResponse.Question(questionId, question.type(), question.text(), converted,
				correct.stream().map(index -> optionId(questionId, index)).toList(), 1, question.explanation());
	}

	private static String optionId(String questionId, int position) {
		return questionId + "o" + position;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	// --- what the model is asked for ---------------------------------------------------------------

	/**
	 * The structured-output shape. Deliberately not {@link QuizDraftResponse}: options are plain
	 * strings and the key is positions into that list, because a model asked to invent ids and then
	 * echo them back in a second field gets it wrong often enough to matter, while a position cannot
	 * be misspelled — only out of range, which is checked.
	 */
	public record Draft(List<DraftQuestion> questions) {
	}

	/** @param correctOptions 1-based positions into {@link #options} */
	public record DraftQuestion(
			QuestionType type,
			String text,
			List<String> options,
			List<Integer> correctOptions,
			String explanation) {
	}
}
