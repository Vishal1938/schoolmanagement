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
	 */
	@Mapping(target = "name", source = "identity.name")
	@Mapping(target = "tagline", source = "identity.tagline")
	@Mapping(target = "logoUrl", source = "identity.logoUrl")
	@Mapping(target = "faviconUrl", source = "identity.faviconUrl")
	@Mapping(target = "theme", source = "identity.theme")
	@Mapping(target = "about", source = "landing.about")
	@Mapping(target = "vision", source = "landing.vision")
	@Mapping(target = "principal", source = "landing.principal")
	@Mapping(target = "academics", source = "landing.academics")
	@Mapping(target = "highlights", source = "landing.highlights")
	@Mapping(target = "facilities", source = "landing.facilities")
	@Mapping(target = "galleryImageUrls", source = "landing.galleryImageUrls")
	@Mapping(target = "stats", source = "landing.stats")
	@Mapping(target = "contact", source = "landing.contact")
	@Mapping(target = "mapEmbedUrl", source = "landing.mapEmbedUrl")
	@Mapping(target = "socialLinks", source = "landing.socialLinks")
	PublicSchoolResponse toPublicResponse(SchoolConfig config);

	// --- request -> document parts ---------------------------------------------------------------

	Identity toIdentity(SchoolConfigRequest.Identity identity);

	Landing toLanding(SchoolConfigRequest.Landing landing);

	GradingScheme toGradingScheme(SchoolConfigRequest.GradingScheme gradingScheme);

	AcademicSettings toAcademicSettings(SchoolConfigRequest.AcademicSettings academicSettings);
}
