package com.school.common.storage;

import java.util.HashMap;
import java.util.Map;

import com.school.common.config.AppProperties;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import com.school.common.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** What reaches object storage, and what is refused before it gets there. */
class ImageUploadServiceTest {

	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02};

	private S3Client s3;
	private ImageUploadService service;

	@BeforeEach
	void setUp() {
		s3 = mock(S3Client.class);
		service = new ImageUploadService(s3, properties(Map.of()));
	}

	@Test
	void storesThePngUnderThePublicPrefixWithTheCategoryFolder() {
		StoredImage stored = service.uploadPublicImage(pngFile(), ImageCategory.LOGO);

		ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3).putObject(captor.capture(), any(RequestBody.class));
		PutObjectRequest request = captor.getValue();

		assertThat(request.bucket()).isEqualTo("school-media");
		assertThat(request.key()).matches("public/logo/[0-9a-f-]{36}\\.png");
		assertThat(request.contentType()).isEqualTo("image/png");
		assertThat(request.contentLength()).isEqualTo(PNG.length);
		assertThat(request.cacheControl()).contains("max-age=31536000");
		// R2 rejects S3 ACLs and MinIO grants public reads by bucket policy, so none is sent.
		assertThat(request.acl()).isNull();
		assertThat(stored.key()).isEqualTo(request.key());
		assertThat(stored.contentType()).isEqualTo("image/png");
	}

	@Test
	void buildsTheUrlFromTheEndpointAndBucketWhenNoPublicDomainIsConfigured() {
		StoredImage stored = service.uploadPublicImage(pngFile(), ImageCategory.GALLERY);

		assertThat(stored.url()).isEqualTo("http://localhost:9000/school-media/" + stored.key());
	}

	@Test
	void buildsTheUrlFromThePublicDomainInProduction() {
		service = new ImageUploadService(s3, properties(Map.of("app.storage.public-base-url", "https://media.test-school.example/")));

		StoredImage stored = service.uploadPublicImage(pngFile(), ImageCategory.FAVICON);

		assertThat(stored.url()).isEqualTo("https://media.test-school.example/" + stored.key());
	}

	@Test
	void refusesAFileThatIsNotAnImageEvenWhenItClaimsToBeOne() {
		MockMultipartFile disguised = new MockMultipartFile("file", "logo.png", "image/png",
				"<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes());

		assertThatThrownBy(() -> service.uploadPublicImage(disguised, ImageCategory.LOGO))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("not a supported image");
		verifyNoInteractions(s3);
	}

	@Test
	void refusesAnEmptyFile() {
		MockMultipartFile empty = new MockMultipartFile("file", "logo.png", "image/png", new byte[0]);

		assertThatThrownBy(() -> service.uploadPublicImage(empty, ImageCategory.LOGO))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("missing or empty");
		verifyNoInteractions(s3);
	}

	@Test
	void refusesAFileOverTheConfiguredLimit() {
		service = new ImageUploadService(s3, properties(Map.of("app.storage.max-image-size", "1KB")));
		MockMultipartFile big = new MockMultipartFile("file", "logo.png", "image/png", new byte[2048]);

		assertThatThrownBy(() -> service.uploadPublicImage(big, ImageCategory.GALLERY))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("too large");
		verifyNoInteractions(s3);
	}

	@Test
	void aStoreThatIsDownIsReportedAsAnUpstreamFailure() {
		when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenThrow(S3Exception.builder()
						.awsErrorDetails(AwsErrorDetails.builder().errorMessage("connection refused").build())
						.build());

		assertThatThrownBy(() -> service.uploadPublicImage(pngFile(), ImageCategory.LOGO))
				.isInstanceOf(AppException.class)
				.satisfies(thrown -> assertThat(((AppException) thrown).errorType())
						.isEqualTo(ErrorType.DEPENDENCY_FAILED));
	}

	@Test
	void detectsTheFormatsWeAcceptAndNothingElse() {
		assertThat(ImageType.detect(PNG)).contains(ImageType.PNG);
		assertThat(ImageType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).contains(ImageType.JPEG);
		assertThat(ImageType.detect("GIF89a".getBytes())).contains(ImageType.GIF);
		assertThat(ImageType.detect(new byte[] {0x00, 0x00, 0x01, 0x00, 0x01})).contains(ImageType.ICO);
		assertThat(ImageType.detect("RIFF____WEBPVP8 ".getBytes())).contains(ImageType.WEBP);
		// RIFF alone is a WAV or an AVI, not an image.
		assertThat(ImageType.detect("RIFF____WAVEfmt ".getBytes())).isEmpty();
		assertThat(ImageType.detect("%PDF-1.7".getBytes())).isEmpty();
		assertThat(ImageType.detect(new byte[] {1})).isEmpty();
	}

	private MockMultipartFile pngFile() {
		return new MockMultipartFile("file", "logo.png", "image/png", PNG);
	}

	/** The local defaults from application.yml, plus whatever the test is varying. */
	private AppProperties properties(Map<String, String> overrides) {
		Map<String, String> values = new HashMap<>(Map.of(
				"app.school-code", "TPS",
				"app.storage.endpoint", "http://localhost:9000",
				"app.storage.bucket", "school-media"));
		values.putAll(overrides);
		return new Binder(new MapConfigurationPropertySource(values))
				.bind("app", AppProperties.class)
				.get();
	}
}
