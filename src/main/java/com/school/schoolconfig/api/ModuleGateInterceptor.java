package com.school.schoolconfig.api;

import java.util.List;

import com.school.common.config.AppProperties;
import com.school.schoolconfig.app.ModuleGate;
import com.school.schoolconfig.domain.SchoolModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Turns a module switch into a 404 on every endpoint behind it.
 *
 * <p>One table instead of a {@code require(...)} line in forty controller methods. The failure mode
 * of the explicit version is what decided it: an endpoint added later that nobody remembers to gate
 * is a feature leaking into a deployment that switched it off, which is the one thing this exists to
 * prevent. A pattern covers whatever is mapped under it, including what is written next year.
 *
 * <p>Patterns are matched against the path <em>after</em> {@code app.api-base-path} is stripped, so
 * the table reads the way API_CONTRACT.md does. First match wins, so anything narrow goes above the
 * {@code /**} that would otherwise swallow it.
 *
 * <p>Deliberately absent: {@code /public/school} and {@code /school/config}. The first is how a client
 * learns which modules are off, so 404-ing it would leave a frontend unable to find out; the second
 * is how an administrator switches one back on, which must not be lockable. {@code landingPage} is
 * therefore not a pattern at all — it is applied inside {@link PublicSchoolController}, which omits
 * the landing block and keeps the identity.
 */
public class ModuleGateInterceptor implements HandlerInterceptor {

	/**
	 * Which module guards which paths, most specific first.
	 *
	 * <p>Report cards and results are the exams module even though they hang off {@code /students}:
	 * that path is the people module's and has to stay reachable, so those two are named rather than
	 * covered by a prefix.
	 */
	private static final List<Gated> GATED = List.of(
			new Gated("/exams/**", SchoolModule.EXAMS),
			new Gated("/students/*/results", SchoolModule.EXAMS),
			new Gated("/students/*/report-card/**", SchoolModule.EXAMS),
			new Gated("/exam-papers/**", SchoolModule.EXAM_PAPERS),
			new Gated("/quizzes/**", SchoolModule.QUIZZES),
			new Gated("/notices/**", SchoolModule.NOTICES),
			new Gated("/public/notices/**", SchoolModule.NOTICES),
			// The daily class register and one student's history. /attendance/employees** is deliberately
			// not here: payroll prorates salaries from it, so it is not the institution's to switch off.
			new Gated("/attendance/class/**", SchoolModule.STUDENT_ATTENDANCE),
			new Gated("/attendance/students/**", SchoolModule.STUDENT_ATTENDANCE),
			// The gateway checkout and its webhook only. The counter flow — /fees/offline-payment,
			// /fees/register/mark-paid — is not here on purpose: an institution that takes no card
			// payments still takes money at the desk. Nor is /payments/{id}/receipt.pdf: a receipt for
			// money already taken must keep downloading whatever is switched off afterwards.
			new Gated("/payments/orders", SchoolModule.ONLINE_PAYMENTS),
			new Gated("/payments/verify", SchoolModule.ONLINE_PAYMENTS),
			new Gated("/public/payments/**", SchoolModule.ONLINE_PAYMENTS),
			new Gated("/ai/**", SchoolModule.AI),
			new Gated("/public/ai/**", SchoolModule.AI),
			// The whole expense book, including the backfill: a deployment that keeps its accounts
			// elsewhere has no use for any of it. The automatic entries are gated inside the listener
			// instead, because an event is not a request and has no path to match.
			new Gated("/accounts/**", SchoolModule.ACCOUNTS));

	private static final AntPathMatcher MATCHER = new AntPathMatcher();

	/** Resolved on first use rather than at construction; see {@link ModuleGateConfig}. */
	private final ObjectProvider<ModuleGate> gate;

	private final String apiBasePath;

	public ModuleGateInterceptor(ObjectProvider<ModuleGate> gate, AppProperties properties) {
		this.gate = gate;
		this.apiBasePath = properties.apiBasePath();
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		SchoolModule module = moduleFor(pathWithinApi(request));
		if (module != null) {
			// Thrown rather than written to the response: DispatcherServlet resolves an exception from
			// preHandle through the same @RestControllerAdvice as one from a controller, so the body is
			// the usual ProblemDetail (CLAUDE.md rule 8) rather than a second error shape invented here.
			gate.getObject().require(module);
		}
		return true;
	}

	/** The request path with the context and API prefixes removed. */
	private String pathWithinApi(HttpServletRequest request) {
		String path = request.getRequestURI();
		String context = request.getContextPath();
		if (context != null && !context.isEmpty() && path.startsWith(context)) {
			path = path.substring(context.length());
		}
		return path.startsWith(apiBasePath) ? path.substring(apiBasePath.length()) : path;
	}

	/** The module guarding this path, or null when nothing does. */
	private static SchoolModule moduleFor(String path) {
		for (Gated gated : GATED) {
			if (MATCHER.match(gated.pattern(), path)) {
				return gated.module();
			}
		}
		return null;
	}

	private record Gated(String pattern, SchoolModule module) {
	}
}
