package com.school.payroll.api;

import java.util.List;

/**
 * What one {@code POST /payroll/records/pay-bulk} did.
 *
 * <p>Deliberately forgiving: a record already paid, or an id that no longer exists, is reported
 * rather than failing the whole batch — otherwise one stale id in a list of fifty means nobody gets
 * paid. Replaying the same list pays nothing twice.
 *
 * @param paid        records moved to PAID by this call
 * @param alreadyPaid ids that were PAID before this call, left exactly as they were
 * @param notFound    ids with no record, usually because the month was re-run
 * @param netPaid     sum of the net of the records this call paid, in paise
 */
public record PayBulkResponse(int paid, List<String> alreadyPaid, List<String> notFound, long netPaid) {
}
