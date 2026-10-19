package com.school.exams.app;

import java.time.LocalDate;

/**
 * Where this session's exams stand, as the admin dashboard reads it.
 *
 * <p>Counts and one date, no exam names: the dashboard answers "is anything waiting on me", and the
 * exams list answers which. {@code marksEntry} is the figure an administrator acts on, because that
 * is the state an exam sits in while it waits for teachers to finish entering marks.
 *
 * @param nextPaperDate the earliest paper on or after today across every exam of the session, or
 *                      null when nothing is scheduled ahead
 */
public record ExamsOverview(int total, int draft, int marksEntry, int published, LocalDate nextPaperDate) {
}
