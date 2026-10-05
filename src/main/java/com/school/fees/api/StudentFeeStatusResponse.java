package com.school.fees.api;

import com.school.common.fees.FeeStatus;

/**
 * {@code GET /fees/students/{uniqueId}/status}.
 *
 * <p>Deliberately just the status. This is the endpoint a teacher may call, and it has no field for
 * an amount — the rule is enforced by the type, not by remembering to null something out.
 */
public record StudentFeeStatusResponse(String studentUniqueId, FeeStatus feeStatus) {
}
