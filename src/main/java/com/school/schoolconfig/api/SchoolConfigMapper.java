package com.school.schoolconfig.api;

import com.school.schoolconfig.domain.AcademicSettings;
import com.school.schoolconfig.domain.GradingScheme;
import com.school.schoolconfig.domain.Identity;
import com.school.schoolconfig.domain.Landing;
import com.school.schoolconfig.domain.Modules;
import com.school.schoolconfig.domain.SchoolConfig;
import com.school.schoolconfig.domain.Terms;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Converts between the {@link SchoolConfig} document and the DTOs. {@code unmappedTargetPolicy =
 * ERROR} means a field added to a response without a mapping breaks the build instead of silently
 * serialising as null.
 *
 * <p>The request side is mapped piece by piece rather than straight to a document: {@code id},
 * {@code code}, {@code version} and the timestamps belong to the service, never to the client.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SchoolConfigMapper {

	// --- document -> DTO -------------------------------------------------------------------------

	/**
	 * {@code institutionType}, {@code terms} and {@code modules} come from the {@code resolved}
	 * accessors rather than straight from the fields, so an admin screen loads the values in force
	 * instead of the nulls a document seeded before they existed actually holds. Loading and PUTting
	 * that response back is then what writes the defaults down.
	 */
	@Mapping(target = "institutionType", expression = "java(config.resolvedInstitutionType())")
	@Mapping(target = "terms", expression = "java(config.resolvedTerms())")
	@Mapping(target = "modules", expression = "java(config.resolvedModules())")
	SchoolConfigResponse toResponse(SchoolConfig config);

	/**
	 * Flattens identity and landing content into the public projection. Every target is listed
	 * explicitly, so nothing reaches this response without a line of code putting it there.
	 *
	 * <p>{@code features} is a second source rather than a field of the document, because it is an
	 * environment switch and not something a school edits. Every other source is qualified with
	 * {@code config.} for the same reason: with two parameters, MapStruct needs to be told which one.
	 */
	@Mapping(target = "name", source = "config.identity.name")
	@Mapping(target = "tagline", source = "config.identity.tagline")
	@Mapping(target = "logoUrl", source = "config.identity.logoUrl")
	@Mapping(target = "faviconUrl", source = "config.identity.faviconUrl")
	@Mapping(target = "theme", source = "config.identity.theme")
	@Mapping(target = "about", source = "config.landing.about")
	@Mapping(target = "vision", source = "config.landing.vision")
	@Mapping(target = "principal", source = "config.landing.principal")
	@Mapping(target = "academics", source = "config.landing.academics")
	@Mapping(target = "highlights", source = "config.landing.highlights")
	@Mapping(target = "facilities", source = "config.landing.facilities")
	@Mapping(target = "galleryImageUrls", source = "config.landing.galleryImageUrls")
	@Mapping(target = "stats", source = "config.landing.stats")
	@Mapping(target = "contact", source = "config.landing.contact")
	@Mapping(target = "mapEmbedUrl", source = "config.landing.mapEmbedUrl")
	@Mapping(target = "socialLinks", source = "config.landing.socialLinks")
	@Mapping(target = "features", source = "features")
	@Mapping(target = "institutionType", expression = "java(config.resolvedInstitutionType())")
	@Mapping(target = "terms", expression = "java(config.resolvedTerms())")
	@Mapping(target = "modules", source = "modules")
	PublicSchoolResponse toPublicResponse(SchoolConfig config, PublicSchoolResponse.Features features,
			Modules modules);

	// --- request -> document parts ---------------------------------------------------------------

	Identity toIdentity(SchoolConfigRequest.Identity identity);

	Landing toLanding(SchoolConfigRequest.Landing landing);

	GradingScheme toGradingScheme(SchoolConfigRequest.GradingScheme gradingScheme);

	AcademicSettings toAcademicSettings(SchoolConfigRequest.AcademicSettings academicSettings);

	/**
	 * The vocabulary to store, given what was asked for and what is there now.
	 *
	 * <p><strong>What is not sent is not changed.</strong> {@code PUT /school/config} replaces the rest
	 * of the configuration wholesale, but these two were added after the admin screen existed: a client
	 * that does not know about them yet sends neither, and that must not silently rename a college's
	 * courses back to "Class". The way to reset a word is to send the school default, not to omit it.
	 */
	default Terms toTerms(SchoolConfigRequest.Terms requested, Terms current) {
		Terms base = current == null ? Terms.SCHOOL : current.withDefaults();
		if (requested == null) {
			return base;
		}
		return new Terms(
				keep(requested.institution(), base.institution()),
				keep(requested.schoolClass(), base.schoolClass()),
				keep(requested.section(), base.section()),
				keep(requested.teacher(), base.teacher()));
	}

	/**
	 * The module switches to store, under the same "not sent is not changed" rule — at both levels, so
	 * sending three switches changes three.
	 *
	 * <p>Written by hand rather than generated because MapStruct would unbox an absent {@link Boolean}
	 * into the {@code false} of the primitive field, which is the opposite of what omitting it means.
	 */
	default Modules toModules(SchoolConfigRequest.Modules requested, Modules current) {
		Modules base = current == null ? Modules.ALL_ON : current;
		if (requested == null) {
			return base;
		}
		return new Modules(
				keep(requested.exams(), base.exams()),
				keep(requested.quizzes(), base.quizzes()),
				keep(requested.examPapers(), base.examPapers()),
				keep(requested.notices(), base.notices()),
				keep(requested.studentLogin(), base.studentLogin()),
				keep(requested.onlinePayments(), base.onlinePayments()),
				keep(requested.landingPage(), base.landingPage()),
				keep(requested.ai(), base.ai()));
	}

	private static String keep(String requested, String current) {
		return requested == null || requested.isBlank() ? current : requested.trim();
	}

	private static boolean keep(Boolean requested, boolean current) {
		return requested == null ? current : requested;
	}
}
