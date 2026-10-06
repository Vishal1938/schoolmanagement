package com.school.schoolconfig.api;

import com.school.common.security.HasPermission;
import com.school.common.storage.ImageCategory;
import com.school.common.storage.ImageUploadService;
import com.school.schoolconfig.app.SchoolConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Administration of the school configuration. Authorised on
 * {@link com.school.common.security.Permission#SCHOOL_CONFIG_MANAGE}, never on a role name.
 */
@RestController
@RequestMapping("/school/config")
@Tag(name = "School configuration", description = "Branding, landing page, grading scheme and academic settings")
public class SchoolConfigController {

	private final SchoolConfigService service;
	private final SchoolConfigMapper mapper;
	private final ImageUploadService images;

	public SchoolConfigController(SchoolConfigService service, SchoolConfigMapper mapper, ImageUploadService images) {
		this.service = service;
		this.mapper = mapper;
		this.images = images;
	}

	@GetMapping
	@PreAuthorize(HasPermission.SCHOOL_CONFIG_MANAGE)
	@Operation(summary = "Read the full configuration")
	public SchoolConfigResponse get() {
		return mapper.toResponse(service.get());
	}

	@PutMapping
	@PreAuthorize(HasPermission.SCHOOL_CONFIG_MANAGE)
	@Operation(summary = "Replace the editable configuration",
			description = "The school code cannot be changed: it comes from SCHOOL_CODE and every unique ID "
					+ "already issued is built from it.")
	public SchoolConfigResponse update(@Valid @RequestBody SchoolConfigRequest request) {
		return mapper.toResponse(service.update(request));
	}

	@PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(HasPermission.SCHOOL_CONFIG_MANAGE)
	@Operation(summary = "Upload a logo, favicon or gallery image",
			description = "Stores the file under the publicly readable prefix and returns the URL to put "
					+ "into the configuration. The URL is not saved anywhere by itself.")
	public ImageUploadResponse uploadImage(@RequestParam("file") MultipartFile file,
			@RequestParam("category") ImageCategory category) {
		return new ImageUploadResponse(images.uploadPublicImage(file, category).url());
	}
}
