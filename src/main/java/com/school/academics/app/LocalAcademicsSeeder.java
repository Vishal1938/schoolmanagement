package com.school.academics.app;

import java.time.LocalDate;
import java.util.List;

import com.school.academics.api.ClassRequest;
import com.school.academics.api.SessionRequest;
import com.school.academics.api.SubjectRequest;
import com.school.academics.domain.Subject;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Gives local development something to attach students, attendance and marks to, so the later tasks
 * can be exercised without clicking a whole curriculum together first.
 *
 * <p>{@code @Profile("local")} only. A real deployment's classes and subjects differ per school and
 * are created by its admin — nothing here is school-agnostic enough to ship. Each of the three
 * collections is seeded only when it is empty, so a restart adds nothing and an edit survives one.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", matchIfMissing = true)
@Order(LocalAcademicsSeeder.ORDER)
public class LocalAcademicsSeeder implements ApplicationRunner {

	/** After the user seeders, which is where the demo teacher comes from. */
	static final int ORDER = 30;

	private static final String SESSION_NAME = "2026-27";
	private static final LocalDate SESSION_START = LocalDate.of(2026, 4, 1);
	private static final LocalDate SESSION_END = LocalDate.of(2027, 3, 31);

	private static final List<SubjectRequest> SUBJECTS = List.of(
			new SubjectRequest("English", "ENG"),
			new SubjectRequest("Hindi", "HIN"),
			new SubjectRequest("Maths", "MAT"),
			new SubjectRequest("Science", "SCI"),
			new SubjectRequest("Social Science", "SST"));

	private static final int CLASS_COUNT = 5;
	private static final List<String> SECTIONS = List.of("A", "B");

	private static final Logger log = LoggerFactory.getLogger(LocalAcademicsSeeder.class);

	private final AcademicSessionService sessions;
	private final SubjectService subjects;
	private final SchoolClassService classes;
	private final AuditService audit;

	public LocalAcademicsSeeder(AcademicSessionService sessions, SubjectService subjects, SchoolClassService classes,
			AuditService audit) {
		this.sessions = sessions;
		this.subjects = subjects;
		this.classes = classes;
		this.audit = audit;
	}

	@Override
	public void run(ApplicationArguments args) {
		boolean seeded = seedSession();
		seeded |= seedSubjects();
		// Classes need the subject ids, so this reads back whatever is there rather than what was just
		// written: on a restart the subjects already existed and nothing was inserted above.
		seeded |= seedClasses();
		if (seeded) {
			audit.record(AuditAction.ACADEMICS_SEEDED, "Academics", "local",
					"Seeded the local session, subjects and classes");
		}
	}

	private boolean seedSession() {
		if (!sessions.list().isEmpty()) {
			log.debug("Academic sessions already present; nothing seeded");
			return false;
		}
		// The first session of a deployment is activated on creation, so this one is current.
		sessions.create(new SessionRequest(SESSION_NAME, SESSION_START, SESSION_END));
		log.info("Seeded local academic session {} and made it current", SESSION_NAME);
		return true;
	}

	private boolean seedSubjects() {
		if (!subjects.list().isEmpty()) {
			log.debug("Subjects already present; nothing seeded");
			return false;
		}
		SUBJECTS.forEach(subjects::create);
		log.info("Seeded {} local subjects", SUBJECTS.size());
		return true;
	}

	private boolean seedClasses() {
		if (!classes.list().isEmpty()) {
			log.debug("Classes already present; nothing seeded");
			return false;
		}
		List<String> subjectIds = subjects.list().stream().map(Subject::getId).toList();
		for (int number = 1; number <= CLASS_COUNT; number++) {
			classes.create(new ClassRequest("Class " + number, number, SECTIONS, subjectIds));
		}
		log.info("Seeded local Classes 1-{}, sections {}, each teaching {} subjects", CLASS_COUNT, SECTIONS,
				subjectIds.size());
		return true;
	}
}
