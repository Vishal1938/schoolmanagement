package com.school.common.storage;

import java.net.URI;

import com.school.common.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * One S3 client for every store we target: MinIO locally, Cloudflare R2 in production, or any other
 * S3-compatible service. Only {@code STORAGE_ENDPOINT} and the credentials change, which is why
 * nothing downstream branches on the environment.
 *
 * <p>Path-style addressing is the default because MinIO and R2 both accept it, while virtual-hosted
 * style would need per-bucket DNS.
 */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {

	@Bean
	@ConditionalOnMissingBean
	public S3Client s3Client(AppProperties properties) {
		AppProperties.Storage storage = properties.storage();
		return S3Client.builder()
				.endpointOverride(URI.create(storage.endpoint()))
				.region(Region.of(storage.region()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(storage.pathStyleAccess())
						.build())
				.build();
	}

	/**
	 * Signs short-lived download URLs for private objects — the exam paper vault (B14).
	 *
	 * <p>Same endpoint and credentials as the client: the URL it produces is only usable if the browser
	 * can reach {@code STORAGE_ENDPOINT}, which is why this is used where a URL is the contract and
	 * streaming through a controller is used elsewhere (notice attachments, receipts).
	 */
	@Bean
	@ConditionalOnMissingBean
	public S3Presigner s3Presigner(AppProperties properties) {
		AppProperties.Storage storage = properties.storage();
		return S3Presigner.builder()
				.endpointOverride(URI.create(storage.endpoint()))
				.region(Region.of(storage.region()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(storage.pathStyleAccess())
						.build())
				.build();
	}
}
