package com.school.payroll.app;

import java.time.YearMonth;
import java.util.Locale;

import com.school.payroll.domain.PayrollRecord;
import com.school.schoolconfig.app.SchoolConfigService;
import org.springframework.stereotype.Service;

/**
 * The salary slip PDF: who may have one, and gathering what it prints.
 *
 * <p>Rendered on every request rather than stored. Unlike a fee receipt — a document handed to a
 * family, which has to come back byte-for-byte on a reprint — a slip is derived entirely from a
 * payroll record that is itself frozen, so rendering it twice gives the same page and keeping a copy
 * in object storage would only be another thing to keep in step.
 *
 * <p>A PENDING record has a slip too, marked PENDING on its face: the office prints it to show
 * somebody what they are about to be paid.
 */
@Service
public class SalarySlipService {

	private final PayrollService payroll;
	private final SchoolConfigService schoolConfig;
	private final SalarySlipPdf pdf;

	public SalarySlipService(PayrollService payroll, SchoolConfigService schoolConfig, SalarySlipPdf pdf) {
		this.payroll = payroll;
		this.schoolConfig = schoolConfig;
		this.pdf = pdf;
	}

	/** The slip for one record — for an admin, or the employee it belongs to. */
	public SalarySlip forRecord(String recordId) {
		PayrollRecord record = payroll.requireReadable(recordId);
		return new SalarySlip(fileName(record), pdf.render(gather(record)));
	}

	/** Everything the renderer needs, read here so the renderer itself touches nothing. */
	private SalarySlipData gather(PayrollRecord record) {
		return new SalarySlipData(
				schoolConfig.identity(),
				schoolConfig.contact(),
				record,
				Months.LONG.format(YearMonth.parse(record.getMonth())),
				schoolConfig.salarySlipFooter());
	}

	/** {@code salary-slip-demo-emp-26-0007-2026-10.pdf}. */
	private static String fileName(PayrollRecord record) {
		String employee = record.getEmployeeUniqueId().toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-|-$", "");
		return "salary-slip-" + employee + "-" + record.getMonth() + ".pdf";
	}
}
