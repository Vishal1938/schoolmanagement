package com.school.payroll.api;

import com.school.payroll.domain.SalaryComponent;

/**
 * One allowance as the API returns it, in paise. Shared by the structure and the payroll record, so
 * a client renders an earnings line the same way wherever it came from.
 */
public record SalaryComponentView(String name, long amount) {

	public static SalaryComponentView of(SalaryComponent component) {
		return new SalaryComponentView(component.name(), component.amount());
	}
}
