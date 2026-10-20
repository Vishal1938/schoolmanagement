package com.school.demodata.api;

import com.school.common.security.HasPermission;
import com.school.demodata.app.DemoDataResetService;
import com.school.demodata.app.DemoDataSeeder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The demonstration data set: write a whole worked year, or wipe it.
 *
 * <p><strong>The whole controller is conditional on {@code app.demo-data.enabled}</strong>
 * ({@code DEMO_DATA_ENABLED}), which is false by default. With it off there is no bean, therefore no
 * mapping, therefore 404 on both paths — which is stronger than a check inside the method, because
 * the code that would write a hundred payments into a school's live database is not reachable at all.
 *
 * <p>Both calls are an administrator's. They are also slow — seeding takes a couple of minutes, since
 * every payment is its own transaction and every day of attendance its own register — so expect to
 * wait rather than to poll.
 */
@RestController
@RequestMapping("/admin/demo-data")
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
@Tag(name = "Demo data", description = "Seeding and wiping the demonstration data set. Present only when "
		+ "DEMO_DATA_ENABLED=true; every path here is 404 otherwise.")
public class DemoDataController {

	private final DemoDataSeeder seeder;
	private final DemoDataResetService reset;

	public DemoDataController(DemoDataSeeder seeder, DemoDataResetService reset) {
		this.seeder = seeder;
		this.reset = reset;
	}

	@PostMapping("/seed")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.DEMO_DATA_MANAGE)
	@Operation(summary = "Seed the demonstration data set",
			description = "Writes a whole 2026-27 college year through the ordinary services: fee heads, "
					+ "semester structures with a per-day late fine, invoices for every student, five "
					+ "backdated payment patterns, three individual charges, salary structures, one "
					+ "advance, a term of staff attendance and four payroll runs — three paid, one left "
					+ "pending. Every payment is taken in date order, so receipt numbers ascend with "
					+ "their dates, and every late fine is charged as of the day the money arrived "
					+ "rather than as of today.\n\n"
					+ "Prerequisites, each of which is a 422 naming what to create: an active session "
					+ "running 2026-07-01 to 2027-06-30, academicSettings.feeCycle of SEMESTER, the "
					+ "courses BA - Year 1/2/3 and BTC - Year 1/2 with at least one section each, and "
					+ "some active students and employees. Individual people named by the data set are "
					+ "matched by name and reported under warnings if they are not found.\n\n"
					+ "409 if any payment or payroll record already exists in the active session: "
					+ "seeding on top of that would double every report. Wipe first, or pass "
					+ "force=true if a wipe was interrupted and left something behind.")
	public DemoDataSeedResponse seed(@RequestParam(defaultValue = "false") boolean force) {
		return DemoDataSeedResponse.of(seeder.seed(force));
	}

	@DeleteMapping
	@PreAuthorize(HasPermission.DEMO_DATA_MANAGE)
	@Operation(summary = "Wipe the demonstration data set",
			description = "For the active session, deletes fee structures, invoices, concessions, "
					+ "payments, receipt PDFs, salary structures, advances, payroll records, employee "
					+ "attendance and holidays, and resets the receipt counter.\n\n"
					+ "Students, employees, user accounts, courses, subjects, sessions, fee heads and "
					+ "the school configuration are not touched, so the college can be seeded again. "
					+ "The one withdrawn student and the one departed employee keep their LEFT status, "
					+ "because this does not touch people — put them back to ACTIVE through "
					+ "PATCH /students/{uniqueId}/status and PATCH /employees/{uniqueId}/status if you "
					+ "want an identical second run.")
	public DemoDataResetResponse reset() {
		return DemoDataResetResponse.of(reset.reset());
	}
}
