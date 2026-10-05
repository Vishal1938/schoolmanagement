package com.school.academics.app;

import com.school.academics.domain.AcademicSession;
import com.school.common.exceptions.BusinessRuleException;
import org.springframework.stereotype.Service;

/**
 * "Which academic year is it?", for every other module.
 *
 * <p>Attendance, exams, fees and payroll all need the current session on nearly every request. They
 * ask here instead of taking a session id from the client — which would let a caller write last
 * year's attendance — and instead of deriving one from today's date, which breaks over the holidays
 * between two sessions.
 */
@Service
public class AcademicContext {

	private final AcademicSessionService sessions;

	public AcademicContext(AcademicSessionService sessions) {
		this.sessions = sessions;
	}

	/**
	 * The active session.
	 *
	 * @throws BusinessRuleException 422 when no session is active. That is a setup mistake rather than
	 *                               a client one, and failing loudly beats quietly writing records that
	 *                               belong to no year.
	 */
	public AcademicSession currentSession() {
		return sessions.findActive().orElseThrow(() -> new BusinessRuleException(
				"No academic session is active. An administrator must create one and activate it "
						+ "before this can be used."));
	}

	/** Id of the active session, which is what documents in other modules store. */
	public String currentSessionId() {
		return currentSession().getId();
	}

	/** Whether the school has been set up far enough to have a current session. */
	public boolean hasCurrentSession() {
		return sessions.findActive().isPresent();
	}
}
