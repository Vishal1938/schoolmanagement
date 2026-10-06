package com.school.fees.api;

import java.util.List;

import com.school.fees.domain.ConcessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /fees/concessions}.
 *
 * <p>{@code sessionId} is absent: a concession is always granted for the active session.
 *
 * @param value   a whole percentage 1–100 for PERCENT, paise for FIXED. The range for PERCENT is
 *                checked in the service, which is the only place that knows the type
 * @param headIds which heads it comes off; null or empty means every head
 * @param reason  required — a discount with no stated reason is unauditable
 */
public record ConcessionRequest(
		@NotBlank String studentUniqueId,
		@NotNull ConcessionType type,
		@Positive long value,
		@Size(max = 40) List<@NotBlank String> headIds,
		@NotBlank @Size(max = 200) String reason) {
}
