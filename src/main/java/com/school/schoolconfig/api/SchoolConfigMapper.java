package com.school.schoolconfig.api;

import com.school.schoolconfig.domain.AcademicSettings;
import com.school.schoolconfig.domain.GradingScheme;
import com.school.schoolconfig.domain.Identity;
import com.school.schoolconfig.domain.Landing;
import com.school.schoolconfig.domain.SchoolConfig;
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
	PublicSchoolResponse toPublicResponse(SchoolConfig config, PublicSchoolResponse.Features features);

	// --- request -> document parts ---------------------------------------------------------------

	Identity toIdentity(SchoolConfigRequest.Identity identity);

	Landing toLanding(SchoolConfigRequest.Landing landing);

	GradingScheme toGradingScheme(SchoolConfigRequest.GradingScheme gradingScheme);

	AcademicSettings toAcademicSettings(SchoolConfigRequest.AcademicSettings academicSettings);
}
