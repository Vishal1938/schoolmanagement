package com.school.ai.app;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.AcademicSessionService;
import com.school.academics.domain.AcademicSession;
import com.school.common.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * The part of an academic session that has actually happened.
 *
 * <p>"Attendance this session" has to be measured over the days that have been and gone, not over
 * the whole year: in April, a session running to next March would read as 4% attendance for a child
 * who has not missed a day. So the range is the session's start to today, or to the session's end
 * once it is over.
 *
 * <p>Today is taken in the school's own timezone. A scan run late in the evening in India must not
 * count tomorrow as a day already attended, nor drop today because UTC has not reached it yet.
 */
@Component
public class SessionProgress {

	private final AcademicContext academicContext;
	private final AcademicSessionService sessions;
	private final Clock clock;
	private final ZoneId zone;

	public SessionProgress(AcademicContext academicContext, AcademicSessionService sessions, Clock clock,
			AppProperties properties) {
		this.academicContext = academicContext;
		this.sessions = sessions;
		this.clock = clock;
		this.zone = ZoneId.of(properties.timezone());
	}

	/**
	 * @param sessionId null or blank for the current session
	 */
	public Window of(String sessionId) {
		AcademicSession session = sessionId == null || sessionId.isBlank()
				? academicContext.currentSession()
				: sessions.get(sessionId);
		LocalDate today = LocalDate.now(clock.withZone(zone));
		LocalDate start = session.getStartDate();
		LocalDate end = session.getEndDate();
		LocalDate until = end != null && end.isBefore(today) ? end : today;
		return new Window(session.getId(), session.getName(), start, start != null && until.isBefore(start)
				// A session that has not started yet: an empty range, which reads as "nothing marked".
				? start
				: until);
	}

	/**
	 * @param from the session's first day; null only if B4 let a session through without one
	 * @param to   today, or the session's last day if it is already over
	 */
	public record Window(String sessionId, String sessionName, LocalDate from, LocalDate to) {
	}
}
