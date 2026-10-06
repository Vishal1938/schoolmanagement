package com.school.attendance.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.school.attendance.api.HolidayRequest;
import com.school.attendance.domain.Holiday;
import com.school.attendance.infra.HolidayRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.NotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** The days the school is shut. Read by anybody, changed by an admin. */
@Service
public class HolidayService {

	private static final String AUDIT_ENTITY = "Holiday";

	private final HolidayRepository holidays;
	private final AuditService audit;
	private final Clock clock;

	public HolidayService(HolidayRepository holidays, AuditService audit, Clock clock) {
		this.holidays = holidays;
		this.audit = audit;
		this.clock = clock;
	}

	/** Holidays in the range, oldest first. Both ends are inclusive. */
	public List<Holiday> between(LocalDate from, LocalDate to) {
		return holidays.findByDateBetweenOrderByDateAsc(from, to);
	}

	public boolean isHoliday(LocalDate date) {
		return holidays.existsByDate(date);
	}

	public Holiday create(HolidayRequest request) {
		if (holidays.existsByDate(request.date())) {
			throw new ConflictException(request.date() + " is already a holiday");
		}
		try {
			Holiday saved = holidays.insert(Holiday.builder()
					.date(request.date())
					.name(request.name().trim())
					.createdAt(Instant.now(clock))
					.build());
			audit.record(AuditAction.HOLIDAY_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
			return saved;
		}
		catch (DuplicateKeyException ex) {
			// The unique index is the real guard; this turns the driver's error into a 409.
			throw new ConflictException(request.date() + " is already a holiday", ex);
		}
	}

	/**
	 * Removes a holiday. Attendance already marked on other days is untouched; the day simply becomes
	 * markable again.
	 */
	public void delete(String id) {
		Holiday holiday = holidays.findById(id).orElseThrow(() -> NotFoundException.of("Holiday", id));
		holidays.delete(holiday);
		audit.record(AuditAction.HOLIDAY_DELETED, AUDIT_ENTITY, id, holiday, null);
	}
}
