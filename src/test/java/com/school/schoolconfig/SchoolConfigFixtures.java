package com.school.schoolconfig;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.school.schoolconfig.api.SchoolConfigMapper;
import com.school.schoolconfig.api.SchoolConfigMapperImpl;
import com.school.schoolconfig.api.SchoolConfigRequest;
import com.school.schoolconfig.domain.GradingMode;
import com.school.schoolconfig.domain.SchoolConfig;

/** Valid configuration for the tests, so each one only states what it is actually about. */
public final class SchoolConfigFixtures {

	public static final SchoolConfigMapper MAPPER = new SchoolConfigMapperImpl();

	private SchoolConfigFixtures() {
	}

	public static SchoolConfigRequest validRequest() {
		return new SchoolConfigRequest(
				new SchoolConfigRequest.Identity("Test Public School", "Learn and lead",
						"https://media.test/public/logo/logo.png", "https://media.test/public/favicon/favicon.ico",
						new SchoolConfigRequest.Theme("#0B3D91", "#F2A93B", "#12B886")),
				new SchoolConfigRequest.Landing(
						"A co-educational school teaching classes I to XII.",
						"Clear thinking, honest speech.",
						new SchoolConfigRequest.Principal("Dr. Anita Deshpande", "Principal",
								"https://media.test/public/gallery/principal.jpg", "Visit us on any working day."),
						new SchoolConfigRequest.Academics("State board", "English medium, Nursery to XII.",
								List.of(new SchoolConfigRequest.AcademicLevel("Primary", "Classes I to V",
										"Reading, writing and arithmetic."))),
						List.of(new SchoolConfigRequest.Highlight("Small classes", "Thirty-five to a room", "users")),
						List.of("Library", "Science laboratories"),
						List.of("https://media.test/public/gallery/campus.jpg"),
						new SchoolConfigRequest.Stats(1240, 68, 32, 98),
						new SchoolConfigRequest.ContactDetails("17 Shastri Marg", null, "Demo City", "MP", "462001",
								"+91 755 400 1200", null, "office@test-school.example", "https://test-school.example"),
						"https://www.google.com/maps/embed?pb=test",
						new SchoolConfigRequest.SocialLinks("https://facebook.com/test", null, null, null, null)),
				new SchoolConfigRequest.GradingScheme(GradingMode.BOTH, List.of(
						new SchoolConfigRequest.GradeBand("A1", 91, 100, "Outstanding"),
						new SchoolConfigRequest.GradeBand("A2", 81, 90, "Excellent"),
						new SchoolConfigRequest.GradeBand("B1", 61, 80, "Very good"),
						new SchoolConfigRequest.GradeBand("C1", 33, 60, "Pass"),
						new SchoolConfigRequest.GradeBand("E", 0, 32, "Needs improvement"))),
				new SchoolConfigRequest.AcademicSettings(
						Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
								DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
						48, "TPS-RCP", "Computer generated receipt.", "Computer generated salary slip.",
						"Computer generated report card."));
	}

	/**
	 * A configuration with every optional field left out: no tagline, logo, favicon, vision, principal,
	 * academics, highlights, facilities, gallery, map or social links. Used to prove the public endpoint
	 * still answers when a school has filled in only the essentials.
	 */
	public static SchoolConfigRequest minimalRequest() {
		SchoolConfigRequest full = validRequest();
		return new SchoolConfigRequest(
				new SchoolConfigRequest.Identity("Test Public School", null, null, null, full.identity().theme()),
				new SchoolConfigRequest.Landing("A co-educational school teaching classes I to XII.",
						null, null, null, null, null, null,
						full.landing().stats(), full.landing().contact(), null, null),
				full.gradingScheme(), full.academicSettings());
	}

	/** The document as it looks after seeding, without going through the database. */
	public static SchoolConfig seeded(String schoolCode, Instant at) {
		return document(validRequest(), schoolCode, at);
	}

	/** {@link #minimalRequest()} as a stored document. */
	public static SchoolConfig seededMinimal(String schoolCode, Instant at) {
		return document(minimalRequest(), schoolCode, at);
	}

	private static SchoolConfig document(SchoolConfigRequest request, String schoolCode, Instant at) {
		return SchoolConfig.builder()
				.id(SchoolConfig.SINGLETON_ID)
				.version(0L)
				.code(schoolCode)
				.identity(MAPPER.toIdentity(request.identity()))
				.landing(MAPPER.toLanding(request.landing()))
				.gradingScheme(MAPPER.toGradingScheme(request.gradingScheme()))
				.academicSettings(MAPPER.toAcademicSettings(request.academicSettings()))
				.createdAt(at)
				.updatedAt(at)
				.build();
	}
}
