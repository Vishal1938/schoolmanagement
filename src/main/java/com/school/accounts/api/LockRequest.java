package com.school.accounts.api;

import java.time.LocalDate;

/**
 * Body of {@code POST /accounts/lock}.
 *
 * @param upTo the last day to close. <strong>Null re-opens everything</strong>, and a date earlier
 *             than the current lock re-opens the difference — both are the unlock this endpoint also
 *             serves, and both are audited as such. A date in the future is refused: a period is
 *             closed once it is over
 */
public record LockRequest(LocalDate upTo) {
}
