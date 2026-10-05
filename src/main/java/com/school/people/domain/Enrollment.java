package com.school.people.domain;

/**
 * Where a student sits right now: which session, class, section and roll number.
 *
 * <p>The session is always the active one at the time of writing — it is never taken from the
 * client, or a request could file this year's admission under last year. Promotion (B5 part 2) moves
 * this forward and keeps the previous values in a history array.
 *
 * @param sessionId the {@code academic_sessions} id this enrollment belongs to
 * @param classId   the {@code classes} id
 * @param section   a section of that class, upper-case
 * @param rollNo    unique within (session, class, section)
 */
public record Enrollment(String sessionId, String classId, String section, int rollNo) {
}
