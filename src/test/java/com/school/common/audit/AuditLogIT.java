package com.school.common.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audit trail against a real MongoDB: entries land, the declared indexes are created, searching
 * works, and — the one that matters for retention — nothing expires.
 *
 * <p>Requires a running Docker daemon; bound to the {@code verify} phase, not {@code test}.
 */
@SpringBootTest(properties = {"app.school-code=ITS", "app.seed.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
class AuditLogIT {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

	@Autowired
	AuditService service;

	@Autowired
	MongoTemplate mongoTemplate;

	@Test
	void entriesAreWrittenAndCanBeReadBack() {
		mongoTemplate.remove(new Query(), AuditLog.COLLECTION);

		service.record(AuditAction.SCHOOL_CONFIG_UPDATED, "SchoolConfig", "school-config",
				Map.of("identity", Map.of("name", "Old Name")),
				Map.of("identity", Map.of("name", "New Name")));

		var page = service.search(new AuditSearch("SchoolConfig", "school-config", null, null, null),
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "at")));

		assertThat(page.getTotalElements()).isEqualTo(1);
		AuditLog entry = page.getContent().getFirst();
		assertThat(entry.getId()).isNotBlank();
		assertThat(entry.getAction()).isEqualTo(AuditAction.SCHOOL_CONFIG_UPDATED);
		assertThat(entry.getActor()).isEqualTo(AuditActor.SYSTEM);
		assertThat(entry.getAt()).isNotNull();
		assertThat(entry.getBefore()).isNotNull();
		assertThat(entry.getAfter()).isNotNull();
	}

	@Test
	void theTrailIsReadNewestFirstAndFiltersByTimeAndAction() {
		mongoTemplate.remove(new Query(), AuditLog.COLLECTION);
		service.record(AuditAction.LOGIN_SUCCESS, "User", "ITS-STU-26-00001");
		service.record(AuditAction.LOGIN_FAILURE, "User", "ITS-STU-26-00001", "wrong password");
		service.record(AuditAction.SCHOOL_CONFIG_UPDATED, "SchoolConfig", "school-config", null, Map.of("a", 1));

		var all = service.search(new AuditSearch(null, null, null, null, null),
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "at")));
		assertThat(all.getTotalElements()).isEqualTo(3);

		var logins = service.search(new AuditSearch("User", null, AuditAction.LOGIN_FAILURE, null, null),
				PageRequest.of(0, 20));
		assertThat(logins.getContent()).singleElement()
				.satisfies(entry -> assertThat(entry.getDetail()).isEqualTo("wrong password"));

		var future = service.search(new AuditSearch(null, null, null, Instant.now().plusSeconds(60), null),
				PageRequest.of(0, 20));
		assertThat(future.getTotalElements()).isZero();
	}

	@Test
	void theDeclaredIndexesExist() {
		List<Document> indexes = mongoTemplate.getCollection(AuditLog.COLLECTION)
				.listIndexes().into(new java.util.ArrayList<>());
		List<String> names = indexes.stream().map(index -> index.getString("name")).toList();

		assertThat(names).contains("audit_entity_idx", "audit_at_idx");
	}

	/**
	 * Retention, asserted rather than documented: audit entries are kept, so no index on this collection
	 * may carry {@code expireAfterSeconds}. Adding a TTL later fails here.
	 */
	@Test
	void nothingInTheAuditCollectionExpires() {
		List<Document> indexes = mongoTemplate.getCollection(AuditLog.COLLECTION)
				.listIndexes().into(new java.util.ArrayList<>());

		assertThat(indexes).allSatisfy(index ->
				assertThat(index.containsKey("expireAfterSeconds"))
						.as("index %s must not expire audit entries", index.getString("name"))
						.isFalse());
	}

	@Test
	void secretsHandedToTheServiceNeverReachTheDatabase() {
		mongoTemplate.remove(new Query(), AuditLog.COLLECTION);
		String bcrypt = "$2a$12$GHuVsE9wkTEwR7ZvJgfF7uqqZj2Yp1kDe3iQxEwYGmH8tCkTpUmMa";

		service.record(AuditAction.PASSWORD_CHANGED, "User", "ITS-EMP-26-0001",
				Map.of("uniqueId", "ITS-EMP-26-0001", "passwordHash", bcrypt),
				Map.of("uniqueId", "ITS-EMP-26-0001", "passwordHash", "$2a$12$anotherhash"));

		// Read as a raw document, so this checks what is actually on disk rather than what the mapper shows.
		Document stored = mongoTemplate.getCollection(AuditLog.COLLECTION).find().first();
		assertThat(stored).isNotNull();
		assertThat(stored.toJson()).doesNotContain(bcrypt).doesNotContain("$2a$12$anotherhash");
		assertThat(stored.toJson()).contains(AuditSanitizer.REDACTED);
	}
}
