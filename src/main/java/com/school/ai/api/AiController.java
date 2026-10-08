package com.school.ai.api;

import java.util.List;

import com.school.ai.app.InsightsService;
import com.school.ai.app.QuizDraftService;
import com.school.ai.app.ReportRemarkService;
import com.school.common.security.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The staff-facing AI endpoints.
 *
 * <p>All three are {@code POST} and none of them writes anything: a drafted quiz, a suggested remark
 * and a list of findings all come back for a person to act on. {@code POST} rather than {@code GET}
 * because each takes a body and none is cacheable or safe to replay from a browser's address bar.
 *
 * <p>With {@code app.features.ai} off every one of these is a 404 — see {@code AiFeatureGateConfig}.
 */
@RestController
@RequestMapping("/ai")
@Tag(name = "AI", description = "Optional AI features, behind app.features.ai")
public class AiController {

	private final QuizDraftService quizDrafts;
	private final ReportRemarkService remarks;
	private final InsightsService insights;

	public AiController(QuizDraftService quizDrafts, ReportRemarkService remarks, InsightsService insights) {
		this.quizDrafts = quizDrafts;
		this.remarks = remarks;
		this.insights = insights;
	}

	@PostMapping("/quiz-draft")
	@PreAuthorize(HasPermission.AI_USE)
	@Operation(summary = "Draft quiz questions on a topic",
			description = "Returns questions in the shape POST /quizzes takes. Nothing is saved. "
					+ "Malformed questions are dropped, so fewer than `count` may come back.")
	public QuizDraftResponse quizDraft(@Valid @RequestBody QuizDraftRequest request) {
		return quizDrafts.draft(request);
	}

	@PostMapping("/report-remarks")
	@PreAuthorize(HasPermission.AI_USE)
	@Operation(summary = "Draft a report-card remark",
			description = "Two or three sentences, from the student's first name, their subject marks, "
					+ "the movement since the previous exam and their attendance. Nothing is saved.")
	public ReportRemarkResponse reportRemark(@Valid @RequestBody ReportRemarkRequest request) {
		return remarks.remark(request);
	}

	@PostMapping("/insights")
	@PreAuthorize(HasPermission.AI_INSIGHTS)
	@Operation(summary = "Students who need attention",
			description = "The flags are computed from the school's records; the model only phrases "
					+ "them. Sorted by number of flags, most first. An empty body scans the whole school.")
	public List<StudentInsight> insights(@Valid @RequestBody(required = false) InsightsRequest request) {
		return insights.insights(request == null ? new InsightsRequest(null, null, null) : request);
	}
}
