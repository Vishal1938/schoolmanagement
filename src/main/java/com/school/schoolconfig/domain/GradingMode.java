package com.school.schoolconfig.domain;

/** How results are reported on report cards (B9). */
public enum GradingMode {

	/** Numeric marks only. */
	MARKS,

	/** Letter grades only, derived from the {@link GradeBand} list. */
	GRADES,

	/** Marks and the matching grade side by side. */
	BOTH
}
