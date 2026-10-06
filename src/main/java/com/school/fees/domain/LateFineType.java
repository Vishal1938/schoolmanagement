package com.school.fees.domain;

/** How a late fine grows once the grace period has passed. */
public enum LateFineType {

	/** One charge, however late the payment is. */
	FLAT,

	/** The charge multiplied by the number of days past the grace period. */
	PER_DAY
}
