package com.school.schoolconfig.domain;

import java.util.List;
import java.util.Optional;

/**
 * The school's grading scheme. B9 uses it to turn a percentage into the grade printed on a report
 * card, which is why the bands live in config and not in code.
 */
public record GradingScheme(GradingMode mode, List<GradeBand> bands) {

	/** The band a percentage falls into, or empty when the bands leave a gap. */
	public Optional<GradeBand> bandFor(int percentage) {
		return bands == null
				? Optional.empty()
				: bands.stream().filter(band -> band.covers(percentage)).findFirst();
	}
}
