/**
 * Application services (the module's public entry point) for the notice module.
 *
 * <p>Exposed as a Spring Modulith named interface for the landing-page chat bot in B18, which puts
 * the current public notices in its system prompt so it answers from what the school has actually
 * announced rather than from the model's imagination.
 *
 * <p>What leaves this module is {@code PublicNoticeBrief}: the title, the body and the publication
 * date of a notice that is already public. The {@code Notice} document, with its audience, author
 * and attachments, stays behind the service.
 */
@org.springframework.modulith.NamedInterface("app")
package com.school.notice.app;
