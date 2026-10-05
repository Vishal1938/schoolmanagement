package com.school.common.storage;

import java.util.Map;
import java.util.Optional;

import com.school.common.config.AppProperties;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import com.school.common.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Private objects in the bucket — notice attachments (B10), and later receipts and the exam-paper
 * vault.
 *
 * <p>The counterpart to {@link ImageUploadService}, which writes under the publicly readable prefix.
 * Nothing written here is reachable without a request this application authorises first: no object
 * ACL is sent, the keys live outside {@code app.storage.public-prefix}, and the bytes are served back
 * through a controller rather than by handing out a URL to the store.
 */
@Component
public class ObjectStorage {

	private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

	private final S3Client s3;
	private final AppProperties.Storage storage;

	public ObjectStorage(S3Client s3, AppProperties properties) {
		this.s3 = s3;
		this.storage = properties.storage();
	}

	/**
	 * Stores an object, overwriting any object already at that key.
	 *
	 * @param metadata user metadata to keep with the object, e.g. the uploader's file name. Values
	 *                 must be ASCII: S3 metadata is sent in HTTP headers.
	 */
	public void put(String key, byte[] bytes, String contentType, Map<String, String> metadata) {
		try {
			s3.putObject(PutObjectRequest.builder()
					.bucket(storage.bucket())
					.key(key)
					.contentType(contentType)
					.contentLength((long) bytes.length)
					.metadata(metadata)
					.build(), RequestBody.fromBytes(bytes));
		}
		catch (S3Exception ex) {
			// Never log the object itself, only where it was going.
			log.error("Upload to {}/{} failed", storage.bucket(), key, ex);
			throw new AppException(ErrorType.DEPENDENCY_FAILED, "The file could not be stored", ex);
		}
		log.info("Stored private object {} ({} bytes)", key, bytes.length);
	}

	/**
	 * Reads an object whole.
	 *
	 * @throws NotFoundException if there is no object at that key
	 */
	public StoredObject get(String key) {
		return find(key).orElseThrow(() -> new NotFoundException("No such file"));
	}

	/**
	 * The same, but empty rather than 404 when the object is not there — for callers where a miss is a
	 * normal answer, such as a receipt PDF that is rendered on first download and cached from then on.
	 *
	 * <p>Note that a missing object is empty while a <em>failed</em> read still throws: "not stored
	 * yet" and "the store is unreachable" must not look alike, or an outage would quietly cause every
	 * receipt to be re-rendered.
	 */
	public Optional<StoredObject> find(String key) {
		try {
			ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(GetObjectRequest.builder()
					.bucket(storage.bucket())
					.key(key)
					.build());
			GetObjectResponse response = object.response();
			return Optional.of(new StoredObject(object.asByteArray(), response.contentType(),
					response.contentLength() == null ? 0 : response.contentLength(),
					response.metadata() == null ? Map.of() : response.metadata()));
		}
		catch (NoSuchKeyException ex) {
			return Optional.empty();
		}
		catch (S3Exception ex) {
			log.error("Read of {}/{} failed", storage.bucket(), key, ex);
			throw new AppException(ErrorType.DEPENDENCY_FAILED, "The file could not be read", ex);
		}
	}

	/**
	 * An object read out of storage.
	 *
	 * @param metadata the user metadata it was stored with, with keys lower-cased by S3
	 */
	public record StoredObject(byte[] bytes, String contentType, long sizeBytes, Map<String, String> metadata) {
	}
}
