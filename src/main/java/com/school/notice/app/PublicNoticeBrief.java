package com.school.notice.app;

import java.time.Instant;

/**
 * A public notice as other modules see it: what it says, and when it was put up.
 *
 * <p>Thinner again than {@code PublicNoticeResponse}, which the landing page gets. No id, because
 * nothing outside this module links to a notice, and no attachments, because those are behind an
 * authenticated download and a caller that cannot fetch one has no use for its name.
 *
 * <p>The one caller is B18's chat bot, which puts these in a system prompt so the bot answers from
 * what the school has actually announced. Only notices already flagged public reach here.
 */
public record PublicNoticeBrief(String title, String body, Instant publishedAt) {
}
