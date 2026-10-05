package com.school.schoolconfig.app;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.config.AppProperties;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.schoolconfig.api.SchoolConfigMapper;
import com.school.schoolconfig.api.SchoolConfigRequest;
import com.school.schoolconfig.domain.ContactDetails;
import com.school.schoolconfig.domain.GradeBand;
import com.school.schoolconfig.domain.GradingMode;
import com.school.schoolconfig.domain.GradingScheme;
import com.school.schoolconfig.domain.Identity;
import com.school.schoolconfig.domain.Principal;
import com.school.schoolconfig.domain.SchoolConfig;
import com.school.schoolconfig.infra.SchoolConfigRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * The only way in or out of the school configuration. Other modules call the narrow accessors at the
 * bottom (receipt prefix for B12, grading scheme for B9, attendance window for B8) instead of
 * reaching for {@code SchoolConfigRepository}.
 */
@Service
public class SchoolConfigService {

	/** Entity type used in the audit trail for this document. */
	private static final String AUDIT_ENTITY = "SchoolConfig";

	private final SchoolConfigRepository repository;
	private final SchoolConfigMapper mapper;
	private final AppProperties properties;
	private final AuditService audit;
	private final Clock clock;

	public SchoolConfigService(SchoolConfigRepository repository, SchoolConfigMapper mapper,
			AppProperties properties, AuditService audit, Clock clock) {
		this.repository = repository;
		this.mapper = mapper;
		this.properties = properties;
		this.audit = audit;
		this.clock = clock;
	}

	/** The configuration of this deployment. Present from the first boot onwards, thanks to seeding. */
	public SchoolConfig get() {
		return repository.findById(SchoolConfig.SINGLETON_ID)
				.orElseThrow(() -> new NotFoundException(
						"The school configuration has not been seeded for this deployment"));
	}

	/** Whether this deployment has been seeded yet. */
	public boolean isSeeded() {
		return repository.existsById(SchoolConfig.SINGLETON_ID);
	}

	/**
	 * Replaces the editable content. {@code code}, the timestamps and the version stay under our
	 * control; a concurrent save loses on the optimistic lock and surfaces as 409.
	 */
	public SchoolConfig update(SchoolConfigRequest request) {
		validateGradingScheme(request.gradingScheme());
		SchoolConfig before = get();
		SchoolConfig updated = before.toBuilder()
				.identity(mapper.toIdentity(request.identity()))
				.landing(mapper.toLanding(request.landing()))
				.gradingScheme(mapper.toGradingScheme(request.gradingScheme()))
				.academicSettings(mapper.toAcademicSettings(request.academicSettings()))
				.updatedAt(Instant.now(clock))
				.build();
		SchoolConfig saved = repository.save(updated);
		audit.record(AuditAction.SCHOOL_CONFIG_UPDATED, AUDIT_ENTITY, SchoolConfig.SINGLETON_ID, before, saved);
		return saved;
	}

	/**
	 * Inserts the seeded configuration if this deployment has none yet.
	 *
	 * @return {@code true} if this call created it, {@code false} if it already existed. The insert
	 *         carries the fixed {@code _id}, so two instances starting together cannot both win: the
	 *         loser sees a duplicate key and reports {@code false}.
	 */
	public boolean seedIfMissing(SchoolConfigRequest seed) {
		if (isSeeded()) {
			return false;
		}
		validateGradingScheme(seed.gradingScheme());
		Instant now = Instant.now(clock);
		SchoolConfig config = SchoolConfig.builder()
				.id(SchoolConfig.SINGLETON_ID)
				.code(properties.schoolCode())
				.identity(mapper.toIdentity(seed.identity()))
				.landing(mapper.toLanding(seed.landing()))
				.gradingScheme(mapper.toGradingScheme(seed.gradingScheme()))
				.academicSettings(mapper.toAcademicSettings(seed.academicSettings()))
				.createdAt(now)
				.updatedAt(now)
				.build();
		try {
			SchoolConfig inserted = repository.insert(config);
			// Actor is SYSTEM: seeding runs at startup, outside any request.
			audit.record(AuditAction.SCHOOL_CONFIG_SEEDED, AUDIT_ENTITY, SchoolConfig.SINGLETON_ID, null, inserted);
			return true;
		}
		catch (DuplicateKeyException ex) {
			return false;
		}
	}

	// --- read models for other modules ------------------------------------------------------------

	/** Prefix of receipt numbers (B12). */
	public String receiptPrefix() {
		return get().getAcademicSettings().receiptPrefix();
	}

	/** Grade bands and reporting mode for report cards (B9). */
	public GradingScheme gradingScheme() {
		return get().getGradingScheme();
	}

	/** How long after marking attendance may still be corrected (B8). */
	public int attendanceEditWindowHours() {
		return get().getAcademicSettings().attendanceEditWindowHours();
	}

	/** Days attendance is expected on (B8). */
	public Set<DayOfWeek> workingDays() {
		return get().getAcademicSettings().workingDays();
	}

	/** School name, logo and colours, for PDF headers (B9, B12, B13). */
	public Identity identity() {
		return get().getIdentity();
	}

	/** Footer text for receipt PDFs (B12). */
	public String receiptFooter() {
		return get().getAcademicSettings().receiptFooter();
	}

	/** Footer text for salary-slip PDFs (B13). */
	public String salarySlipFooter() {
		return get().getAcademicSettings().salarySlipFooter();
	}

	/** Footer text for report-card PDFs (B9). */
	public String reportCardFooter() {
		return get().getAcademicSettings().reportCardFooter();
	}

	/** Postal address and office contacts, for PDF headers (B9, B12, B13). */
	public ContactDetails contact() {
		return get().getLanding().contact();
	}

	/**
	 * Who signs for the school, for the signature line on PDFs (B9). Null when the school has not
	 * named a principal — the line is still printed, just without a name under it.
	 */
	public Principal principal() {
		return get().getLanding().principal();
	}

	// --- validation Bean Validation cannot express ------------------------------------------------

	/**
	 * Grade bands must not overlap or repeat a label, and when grades are actually reported they must
	 * cover 0-100 without a gap — otherwise a percentage in the gap has no grade to print.
	 */
	private void validateGradingScheme(SchoolConfigRequest.GradingScheme scheme) {
		List<SchoolConfigRequest.GradeBand> bands = scheme.bands();
		List<FieldViolation> violations = new ArrayList<>();

		for (int i = 0; i < bands.size(); i++) {
			if (bands.get(i).minPercentage() > bands.get(i).maxPercentage()) {
				violations.add(new FieldViolation("gradingScheme.bands[" + i + "].minPercentage",
						"must not be greater than maxPercentage"));
			}
		}

		Set<String> labels = new HashSet<>();
		for (int i = 0; i < bands.size(); i++) {
			if (!labels.add(bands.get(i).grade())) {
				violations.add(new FieldViolation("gradingScheme.bands[" + i + "].grade",
						"duplicates another band's grade"));
			}
		}

		if (violations.isEmpty()) {
			List<SchoolConfigRequest.GradeBand> sorted = bands.stream()
					.sorted(Comparator.comparingInt(SchoolConfigRequest.GradeBand::minPercentage))
					.toList();
			for (int i = 1; i < sorted.size(); i++) {
				if (sorted.get(i).minPercentage() <= sorted.get(i - 1).maxPercentage()) {
					violations.add(new FieldViolation("gradingScheme.bands",
							"bands " + sorted.get(i - 1).grade() + " and " + sorted.get(i).grade() + " overlap"));
				}
			}
			if (scheme.mode() != GradingMode.MARKS) {
				violations.addAll(coverageViolations(sorted));
			}
		}

		if (!violations.isEmpty()) {
			throw new ValidationException("The grading scheme is not usable", violations);
		}
	}

	private List<FieldViolation> coverageViolations(List<SchoolConfigRequest.GradeBand> sorted) {
		List<FieldViolation> violations = new ArrayList<>();
		if (sorted.get(0).minPercentage() != 0) {
			violations.add(new FieldViolation("gradingScheme.bands", "the lowest band must start at 0"));
		}
		if (sorted.get(sorted.size() - 1).maxPercentage() != 100) {
			violations.add(new FieldViolation("gradingScheme.bands", "the highest band must end at 100"));
		}
		for (int i = 1; i < sorted.size(); i++) {
			if (sorted.get(i).minPercentage() != sorted.get(i - 1).maxPercentage() + 1) {
				violations.add(new FieldViolation("gradingScheme.bands", "no band covers "
						+ (sorted.get(i - 1).maxPercentage() + 1) + "%"));
			}
		}
		return violations;
	}

	/** Kept for the report-card code in B9, which resolves a percentage to a label. */
	public GradeBand gradeFor(int percentage) {
		return gradingScheme().bandFor(percentage)
				.orElseThrow(() -> new NotFoundException("No grade band covers " + percentage + "%"));
	}
}
