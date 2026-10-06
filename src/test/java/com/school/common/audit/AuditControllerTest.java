package com.school.common.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.school.schoolconfig.infra.SchoolConfigRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /audit} against the real security chain. The trail says who did what, so it is for
 * administrators only.
 */
@SpringBootTest(properties = {
		"app.seed.enabled=false",
		"management.health.mongo.enabled=false",
		"spring.data.mongodb.auto-index-creation=false"
})
@AutoConfigureMockMvc
class AuditControllerTest {

	private static final Instant NOW = Instant.parse("2026-04-01T09:30:00Z");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AuditService auditService;

	@MockitoBean
	SchoolConfigRepository schoolConfigRepository;

	@MockitoBean
	S3Client s3Client;

	@Test
	@WithAnonymousUser
	void readingTheTrailNeedsAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/audit"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@WithMockUser(authorities = "SCHOOL_CONFIG_MANAGE")
	void managingTheSchoolDoesNotImplyReadingTheTrail() throws Exception {
		mockMvc.perform(get("/api/v1/audit"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	@WithMockUser(authorities = "AUDIT_READ")
	void returnsThePagedTrailInTheContractsShape() throws Exception {
		when(auditService.search(any(AuditSearch.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(entry()), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/audit"))
				.andExpect(status().isOk())
				// Field names from API_CONTRACT.md §1: items and totalItems, not content/totalElements.
				.andExpect(jsonPath("$.items[0].action").value("SCHOOL_CONFIG_UPDATED"))
				.andExpect(jsonPath("$.items[0].entityType").value("SchoolConfig"))
				.andExpect(jsonPath("$.items[0].entityId").value("school-config"))
				.andExpect(jsonPath("$.items[0].actor.uniqueId").value("DEMO-EMP-26-0001"))
				.andExpect(jsonPath("$.items[0].at").value(NOW.toString()))
				.andExpect(jsonPath("$.items[0].before.identity.name").value("Old Name"))
				.andExpect(jsonPath("$.items[0].after.identity.name").value("New Name"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalItems").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.content").doesNotExist())
				.andExpect(jsonPath("$.totalElements").doesNotExist());
	}

	@Test
	@WithMockUser(authorities = "AUDIT_READ")
	void filtersAndPagingReachTheService() throws Exception {
		when(auditService.search(any(AuditSearch.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

		mockMvc.perform(get("/api/v1/audit")
						.param("entityType", "SchoolConfig")
						.param("entityId", "school-config")
						.param("action", "SCHOOL_CONFIG_UPDATED")
						.param("from", "2026-03-01T00:00:00Z")
						.param("to", "2026-04-01T00:00:00Z")
						.param("page", "1")
						.param("size", "5"))
				.andExpect(status().isOk());

		ArgumentCaptor<AuditSearch> search = ArgumentCaptor.forClass(AuditSearch.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		org.mockito.Mockito.verify(auditService).search(search.capture(), pageable.capture());
		assertThat(search.getValue().entityType()).isEqualTo("SchoolConfig");
		assertThat(search.getValue().entityId()).isEqualTo("school-config");
		assertThat(search.getValue().action()).isEqualTo(AuditAction.SCHOOL_CONFIG_UPDATED);
		assertThat(search.getValue().from()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
		assertThat(search.getValue().to()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
	}

	@Test
	@WithMockUser(authorities = "AUDIT_READ")
	void theDefaultSortIsNewestFirst() throws Exception {
		when(auditService.search(any(AuditSearch.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		mockMvc.perform(get("/api/v1/audit")).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		org.mockito.Mockito.verify(auditService).search(any(AuditSearch.class), pageable.capture());
		assertThat(pageable.getValue().getSort().getOrderFor("at")).isNotNull()
				.satisfies(order -> assertThat(order.isDescending()).isTrue());
	}

	/** API_CONTRACT.md §1 caps page size at 100, enforced by spring.data.web.pageable.max-page-size. */
	@Test
	@WithMockUser(authorities = "AUDIT_READ")
	void anOversizedPageIsCappedRatherThanHonoured() throws Exception {
		when(auditService.search(any(AuditSearch.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

		mockMvc.perform(get("/api/v1/audit").param("size", "5000")).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		org.mockito.Mockito.verify(auditService).search(any(AuditSearch.class), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
	}

	@Test
	@WithMockUser(authorities = "AUDIT_READ")
	void anUnknownActionIsAValidationErrorRatherThanA500() throws Exception {
		mockMvc.perform(get("/api/v1/audit").param("action", "NOT_AN_ACTION"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	private AuditLog entry() {
		return AuditLog.builder()
				.id("64f0c0ffee00000000000001")
				.action(AuditAction.SCHOOL_CONFIG_UPDATED)
				.entityType("SchoolConfig")
				.entityId("school-config")
				.actor(new AuditActor(null, "DEMO-EMP-26-0001", "ADMIN"))
				.at(NOW)
				.before(Map.of("identity", Map.of("name", "Old Name")))
				.after(Map.of("identity", Map.of("name", "New Name")))
				.build();
	}
}
