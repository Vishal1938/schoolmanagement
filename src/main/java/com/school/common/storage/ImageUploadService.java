package com.school.common.storage;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.school.common.config.AppProperties;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Stores the school's public images — logo, favicon and gallery — under the publicly readable prefix.
 *
 * <p>Two deliberate choices keep the same code working against MinIO and R2:
 * <ul>
 * <li>No object ACL is sent. MinIO grants anonymous reads through the bucket policy that docker
 * compose applies to the {@code public/} prefix; R2 rejects S3 ACLs outright and publishes through a
 * bucket URL or custom domain instead.</li>
 * <li>The returned URL is built from {@code app.storage.public-base-url}, not from the endpoint the
 * client uploaded to, because in production the write endpoint and the read domain differ.</li>
 * </ul>
 *
 * <p>The format is decided by reading the file's leading bytes. A client-supplied
 * {@code Content-Type} is never trusted, and the stored object is given the detected type so a
 * disguised file cannot be served back as something else.
 */
@Service
public class ImageUploadService {

	private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);

	/** Keys contain a random UUID and are never reused, so the object can be cached indefinitely. */
	private static final String CACHE_CONTROL = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().getHeaderValue();

	private final S3Client s3;
	private final AppProperties.Storage storage;

	public ImageUploadService(S3Client s3, AppProperties properties) {
		this.s3 = s3;
		this.storage = properties.storage();
	}

	/**
	 * Validates and stores an image, returning where it can be read from.
	 *
	 * @throws ValidationException if the file is empty, too large, or not one of the supported image
	 *                             formats
	 */
	public StoredImage uploadPublicImage(MultipartFile file, ImageCategory category) {
		byte[] bytes = read(file);
		ImageType type = identify(bytes, file);
		String key = storage.publicPrefix() + category.folder() + "/" + UUID.randomUUID() + "." + type.extension();

		try {
			s3.putObject(PutObjectRequest.builder()
					.bucket(storage.bucket())
					.key(key)
					.contentType(type.contentType())
					.contentLength((long) bytes.length)
					.cacheControl(CACHE_CONTROL)
					.build(), RequestBody.fromBytes(bytes));
		}
		catch (S3Exception ex) {
			// Never log the upload itself, only where it was going.
			log.error("Upload to {}/{} failed", storage.bucket(), key, ex);
			throw new AppException(ErrorType.DEPENDENCY_FAILED, "The image could not be stored", ex);
		}

		String url = storage.resolvedPublicBaseUrl() + "/" + key;
		log.info("Stored public image {} ({} bytes)", key, bytes.length);
		return new StoredImage(key, url, type.contentType(), bytes.length);
	}

	private byte[] read(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ValidationException("The image is missing or empty",
					List.of(new FieldViolation("file", "must not be empty")));
		}
		long limit = storage.maxImageSize().toBytes();
		if (file.getSize() > limit) {
			throw new ValidationException("The image is too large",
					List.of(new FieldViolation("file", "must not be larger than " + storage.maxImageSize().toMegabytes()
							+ " MB")));
		}
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new ValidationException("The image could not be read",
					List.of(new FieldViolation("file", "could not be read")));
		}
	}

	private ImageType identify(byte[] bytes, MultipartFile file) {
		return ImageType.detect(bytes).orElseThrow(() -> {
			// The declared type is logged, not trusted; it is a useful hint when a client misbehaves.
			log.debug("Rejected upload declared as {}", file.getContentType());
			return new ValidationException("The file is not a supported image",
					List.of(new FieldViolation("file", "must be a PNG, JPEG, GIF, WebP or ICO image")));
		});
	}
}
