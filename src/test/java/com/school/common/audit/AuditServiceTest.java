package com.school.common.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What lands in an audit entry: the actor, the time, and sanitized before-and-after state. */
class AuditServiceTest {

	private static final Instant NOW = Instant.parse("2026-04-01T09:30:00Z");
	private static final String BCRYPT = "$2a$12$GHuVsE9wkTEwR7ZvJgfF7uqqZj2Yp1kDe3iQxEwYGmH8tCkTpUmMa";

	private AuditLogRepository repository;
	private MongoOperations mongo;
	private AuditService service;

	@BeforeEach
	void setUp() {
		repository = mock(AuditLogRepository.class);
		mongo = mock(MongoOperations.class);
		when(repository.insert(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
		service = new AuditService(repository, mongo, new AuditSanitizer(new ObjectMapper()),
				new AuditActorResolver(), Clock.fixed(NOW, ZoneId.of("Asia/Kolkata")));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void recordsTheActorFromTheSecurityContext() {
		authenticateAs("DEMO-EMP-26-0001");

		service.record(AuditAction.SCHOOL_CONFIG_UPDATED, "SchoolConfig", "school-config", Map.of("a", 1),
				Map.of("a", 2));

		AuditLog stored = captureStored();
		assertThat(stored.getActor().uniqueId()).isEqualTo("DEMO-EMP-26-0001");
		assertThat(stored.getAt()).isEqualTo(NOW);
		assertThat(stored.getAction()).isEqualTo(AuditAction.SCHOOL_CONFIG_UPDATED);
		assertThat(stored.getEntityType()).isEqualTo("SchoolConfig");
		assertThat(stored.getEntityId()).isEqualTo("school-config");
		assertThat(stored.getBefore()).containsEntry("a", 1);
		assertThat(stored.getAfter()).containsEntry("a", 2);
	}

	@Test
	void anUnauthenticatedRequestIsRecordedAsAnonymous() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
				"key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		service.record(AuditAction.LOGIN_FAILURE, "User", "DEMO-STU-26-00001", "bad password");

		assertThat(captureStored().getActor()).isEqualTo(AuditActor.ANONYMOUS);
	}

	@Test
	void workWithNoRequestAtAllIsRecordedAsSystem() {
		// No security context: startup seeding, or a background job.
		service.record(AuditAction.SCHOOL_CONFIG_SEEDED, "SchoolConfig", "school-config", null, Map.of("a", 1));

		AuditLog stored = captureStored();
		assertThat(stored.getActor()).isEqualTo(AuditActor.SYSTEM);
		assertThat(stored.getBefore()).isNull();
		assertThat(stored.getAfter()).isNotNull();
	}

	@Test
	void sanitizesWhateverTheCallerHandsOver() {
		authenticateAs("DEMO-EMP-26-0001");
		record UserLike(String uniqueId, String passwordHash) {
		}

		service.record(AuditAction.PASSWORD_CHANGED, "User", "DEMO-EMP-26-0001",
				new UserLike("DEMO-EMP-26-0001", BCRYPT), new UserLike("DEMO-EMP-26-0001", "$2a$12$somethingelse"));

		AuditLog stored = captureStored();
		assertThat(stored.getBefore()).containsEntry("passwordHash", AuditSanitizer.REDACTED);
		assertThat(stored.getAfter()).containsEntry("passwordHash", AuditSanitizer.REDACTED);
		assertThat(stored.toString() + stored.getBefore() + stored.getAfter()).doesNotContain(BCRYPT);
	}

	@Test
	void anActionWithNoStateStoresNeitherBeforeNorAfter() {
		authenticateAs("DEMO-STU-26-00001");

		service.record(AuditAction.LOGIN_SUCCESS, "User", "DEMO-STU-26-00001");

		AuditLog stored = captureStored();
		assertThat(stored.getBefore()).isNull();
		assertThat(stored.getAfter()).isNull();
		assertThat(stored.getDetail()).isNull();
	}

	@Test
	void searchWithNoFiltersReadsEverythingNewestFirst() {
		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "at"));
		when(mongo.count(any(Query.class), eq(AuditLog.class))).thenReturn(3L);
		when(mongo.find(any(Query.class), eq(AuditLog.class))).thenReturn(List.of(entry(), entry(), entry()));

		Page<AuditLog> page = service.search(new AuditSearch(null, null, null, null, null), pageable);

		assertThat(page.getTotalElements()).isEqualTo(3);
		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		verify(mongo).find(query.capture(), eq(AuditLog.class));
		assertThat(query.getValue().getQueryObject()).isEmpty();
		assertThat(query.getValue().getSortObject().get("at")).isEqualTo(-1);
	}

	@Test
	void everyFilterNarrowsTheQuery() {
		when(mongo.count(any(Query.class), eq(AuditLog.class))).thenReturn(1L);
		when(mongo.find(any(Query.class), eq(AuditLog.class))).thenReturn(List.of(entry()));

		service.search(new AuditSearch("SchoolConfig", "school-config", AuditAction.SCHOOL_CONFIG_UPDATED,
				NOW.minusSeconds(60), NOW), PageRequest.of(0, 20));

		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		verify(mongo).find(query.capture(), eq(AuditLog.class));
		// Inspected structurally rather than as JSON: the criteria hold the enum itself, and Spring Data
		// converts it when the query runs, so toJson() here would need a codec registry.
		@SuppressWarnings("unchecked")
		List<Document> conditions = (List<Document>) query.getValue().getQueryObject().get("$and");
		assertThat(conditions).anySatisfy(condition ->
						assertThat(condition.get("entityType")).isEqualTo("SchoolConfig"))
				.anySatisfy(condition -> assertThat(condition.get("entityId")).isEqualTo("school-config"))
				.anySatisfy(condition -> assertThat(condition.get("action"))
						.isEqualTo(AuditAction.SCHOOL_CONFIG_UPDATED))
				.anySatisfy(condition -> assertThat(condition.get("at", Document.class))
						.containsEntry("$gte", NOW.minusSeconds(60)))
				.anySatisfy(condition -> assertThat(condition.get("at", Document.class))
						.containsEntry("$lt", NOW));
	}

	@Test
	void blankFiltersAreIgnoredRatherThanMatchingEmptyStrings() {
		when(mongo.count(any(Query.class), eq(AuditLog.class))).thenReturn(0L);
		when(mongo.find(any(Query.class), eq(AuditLog.class))).thenReturn(List.of());

		service.search(new AuditSearch("  ", "", null, null, null), PageRequest.of(0, 20));

		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		verify(mongo).find(query.capture(), eq(AuditLog.class));
		assertThat(query.getValue().getQueryObject()).isEmpty();
	}

	private void authenticateAs(String uniqueId) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				uniqueId, null, AuthorityUtils.createAuthorityList("SCHOOL_CONFIG_MANAGE")));
	}

	private AuditLog captureStored() {
		ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
		verify(repository).insert(captor.capture());
		return captor.getValue();
	}

	private AuditLog entry() {
		return AuditLog.builder()
				.action(AuditAction.SCHOOL_CONFIG_UPDATED)
				.entityType("SchoolConfig")
				.entityId("school-config")
				.actor(AuditActor.SYSTEM)
				.at(NOW)
				.build();
	}
}
