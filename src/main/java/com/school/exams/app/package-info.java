/**
 * Application services (the module's public entry point) for the exams module.
 *
 * <p>Exposed as a Spring Modulith named interface so the ai module (B18) can ask how a student is
 * doing: the report remark needs one exam and the one before it, and the at-risk insights need the
 * last two published exams of a class.
 *
 * <p>What leaves this module is {@code StudentExamScore} and nothing else. Neither the {@code Exam}
 * document nor {@code ExamResult} — the full report-card payload, ranks included — crosses the
 * boundary, so a caller outside exams can learn a percentage and which papers were failed, and
 * cannot learn who came first.
 */
@org.springframework.modulith.NamedInterface("app")
package com.school.exams.app;
