package com.school.common.fees;

import java.util.Collection;
import java.util.Map;

/**
 * "How does this student stand on fees?", answered by the fees module for the student projections in
 * people (B5) and the dashboards in B16.
 *
 * <p>An interface rather than a direct call so that the only thing which ever crosses a module
 * boundary is a status — never an invoice or an amount — and so people does not import fees. See the
 * package javadoc for why it lives here rather than in {@code com.school.fees.app}.
 */
public interface StudentFeeStatusProvider {

	FeeStatus statusFor(String studentUniqueId);

	/**
	 * The same thing for a page of students at once. The list endpoint shows a status per row, and one
	 * query beats one per row.
	 */
	Map<String, FeeStatus> statusesFor(Collection<String> studentUniqueIds);
}
