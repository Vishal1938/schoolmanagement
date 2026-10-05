package com.school.attendance.infra;

import java.time.LocalDate;
import java.util.List;

import com.school.attendance.domain.Holiday;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository for {@code holidays}. Internal to this module; callers use {@code HolidayService}. */
public interface HolidayRepository extends MongoRepository<Holiday, String> {

	List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

	boolean existsByDate(LocalDate date);

	boolean existsByDateIn(List<LocalDate> dates);
}
