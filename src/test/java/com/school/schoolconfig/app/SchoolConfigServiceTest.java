package com.school.schoolconfig.app;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.config.AppProperties;
import com.school.common.exceptions.ErrorType;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.schoolconfig.SchoolConfigFixtures;
import com.school.schoolconfig.api.SchoolConfigRequest;
import com.school.schoolconfig.domain.GradingMode;
import com.school.schoolconfig.domain.SchoolConfig;
import com.school.schoolconfig.infra.SchoolConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The rules the school configuration enforces on its own, independently of HTTP. */
class SchoolConfigServiceTest {

	private static final Instant NOW = Instant.parse("2026-04-01T09:30:00Z");
	private static final String SCHOOL_CODE = "TPS";

	private SchoolConfigRepository repository;
	private AuditService audit;
	private SchoolConfigService service;

	@BeforeEach
	void setUp() {
		repository = mock(SchoolConfigRepository.class);
		audit = mock(AuditService.class);
		AppProperties properties = new Binder(new MapConfigurationPropertySource(java.util.Map.of(
						"app.school-code", SCHOOL_CODE)))
				.bind("app", AppProperties.class)
				.get();
		service = new SchoolConfigService(repository, SchoolConfigFixtures.MAPPER, properties, audit,
				Clock.fixed(NOW, ZoneId.of("Asia/Kolkata")));
	}

	@Test
	void seedingStoresTheSingletonWithTheCodeFromTheEnvironment() {
		when(repository.existsById(SchoolConfig.SINGLETON_ID)).thenReturn(false);

		boolean seeded = service.seedIfMissing(SchoolConfigFixtures.validRequest());

		assertThat(seeded).isTrue();
		org.mockito.ArgumentCaptor<SchoolConfig> captor = org.mockito.ArgumentCaptor.forClass(SchoolConfig.class);
		verify(repository).insert(captor.capture());
		SchoolConfig stored = captor.getValue();
		assertThat(stored.getId()).isEqualTo(SchoolConfig.SINGLETON_ID);
		assertThat(stored.getCode()).isEqualTo(SCHOOL_CODE);
		assertThat(stored.getIdentity().name()).isEqualTo("Test Public School");
		assertThat(stored.getAcademicSettings().receiptPrefix()).isEqualTo("TPS-RCP");
		assertThat(stored.getCreatedAt()).isEqualTo(NOW);
		assertThat(stored.getUpdatedAt()).isEqualTo(NOW);
	}

	@Test
	void seedingIsSkippedWhenTheDeploymentAlreadyHasConfiguration() {
		when(repository.existsById(SchoolConfig.SINGLETON_ID)).thenReturn(true);

		assertThat(service.seedIfMissing(SchoolConfigFixtures.validRequest())).isFalse();
		verify(repository, never()).insert(any(SchoolConfig.class));
	}

	@Test
	void twoInstancesSeedingAtOnceLeaveOneDocument() {
		when(repository.existsById(SchoolConfig.SINGLETON_ID)).thenReturn(false);
		when(repository.insert(any(SchoolConfig.class))).thenThrow(new DuplicateKeyException("_id"));

		// The loser reports that it did not seed rather than failing the boot.
		assertThat(service.seedIfMissing(SchoolConfigFixtures.validRequest())).isFalse();
	}

	@Test
	void readingBeforeSeedingIsANotFound() {
		when(repository.findById(SchoolConfig.SINGLETON_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get())
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("not been seeded");
	}

	@Test
	void updateReplacesContentButKeepsTheCodeAndCreationTime() {
		Instant seededAt = NOW.minusSeconds(86_400);
		when(repository.findById(SchoolConfig.SINGLETON_ID))
				.thenReturn(Optional.of(SchoolConfigFixtures.seeded(SCHOOL_CODE, seededAt)));
		when(repository.save(any(SchoolConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

		SchoolConfigRequest request = SchoolConfigFixtures.validRequest();
		SchoolConfigRequest renamed = new SchoolConfigRequest(
				new SchoolConfigRequest.Identity("Renamed School", request.identity().tagline(),
						request.identity().logoUrl(), request.identity().faviconUrl(), request.identity().theme()),
				request.landing(), request.gradingScheme(), request.academicSettings());

		SchoolConfig updated = service.update(renamed);

		assertThat(updated.getIdentity().name()).isEqualTo("Renamed School");
		assertThat(updated.getCode()).isEqualTo(SCHOOL_CODE);
		assertThat(updated.getCreatedAt()).isEqualTo(seededAt);
		assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
	}

	@Test
	void updatingIsAuditedWithTheStateBeforeAndAfter() {
		SchoolConfig before = SchoolConfigFixtures.seeded(SCHOOL_CODE, NOW.minusSeconds(86_400));
		when(repository.findById(SchoolConfig.SINGLETON_ID)).thenReturn(Optional.of(before));
		when(repository.save(any(SchoolConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

		SchoolConfigRequest request = SchoolConfigFixtures.validRequest();
		SchoolConfigRequest renamed = new SchoolConfigRequest(
				new SchoolConfigRequest.Identity("Renamed School", request.identity().tagline(),
						request.identity().logoUrl(), request.identity().faviconUrl(), request.identity().theme()),
				request.landing(), request.gradingScheme(), request.academicSettings());

		service.update(renamed);

		org.mockito.ArgumentCaptor<SchoolConfig> beforeState = org.mockito.ArgumentCaptor.forClass(SchoolConfig.class);
		org.mockito.ArgumentCaptor<SchoolConfig> afterState = org.mockito.ArgumentCaptor.forClass(SchoolConfig.class);
		verify(audit).record(org.mockito.ArgumentMatchers.eq(AuditAction.SCHOOL_CONFIG_UPDATED),
				org.mockito.ArgumentMatchers.eq("SchoolConfig"),
				org.mockito.ArgumentMatchers.eq(SchoolConfig.SINGLETON_ID),
				beforeState.capture(), afterState.capture());
		assertThat(beforeState.getValue().getIdentity().name()).isEqualTo("Test Public School");
		assertThat(afterState.getValue().getIdentity().name()).isEqualTo("Renamed School");
	}

	@Test
	void seedingIsAuditedAsACreationBySystem() {
		when(repository.existsById(SchoolConfig.SINGLETON_ID)).thenReturn(false);
		when(repository.insert(any(SchoolConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.seedIfMissing(SchoolConfigFixtures.validRequest());

		// No before state, because there was nothing there.
		verify(audit).record(org.mockito.ArgumentMatchers.eq(AuditAction.SCHOOL_CONFIG_SEEDED),
				org.mockito.ArgumentMatchers.eq("SchoolConfig"),
				org.mockito.ArgumentMatchers.eq(SchoolConfig.SINGLETON_ID),
				org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.any(SchoolConfig.class));
	}

	@Test
	void aSeedingRaceLostToAnotherInstanceIsNotAudited() {
		when(repository.existsById(SchoolConfig.SINGLETON_ID)).thenReturn(false);
		when(repository.insert(any(SchoolConfig.class))).thenThrow(new DuplicateKeyException("_id"));

		service.seedIfMissing(SchoolConfigFixtures.validRequest());

		org.mockito.Mockito.verifyNoInteractions(audit);
	}

	@Test
	void overlappingGradeBandsAreRejected() {
		SchoolConfigRequest request = withBands(GradingMode.BOTH,
				new SchoolConfigRequest.GradeBand("A", 50, 100, null),
				new SchoolConfigRequest.GradeBand("B", 0, 60, null));

		assertThatThrownBy(() -> service.seedIfMissing(request))
				.isInstanceOf(ValidationException.class)
				.satisfies(thrown -> {
					ValidationException ex = (ValidationException) thrown;
					assertThat(ex.errorType()).isEqualTo(ErrorType.VALIDATION_ERROR);
					assertThat(ex.violations()).anySatisfy(violation ->
							assertThat(violation.message()).contains("overlap"));
				});
	}

	@Test
	void gradeBandsMustNotLeaveAPercentageWithoutAGrade() {
		SchoolConfigRequest request = withBands(GradingMode.GRADES,
				new SchoolConfigRequest.GradeBand("A", 51, 100, null),
				new SchoolConfigRequest.GradeBand("B", 0, 40, null));

		assertThatThrownBy(() -> service.seedIfMissing(request))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("not usable");
	}

	@Test
	void aBandEndingBeforeItStartsIsRejected() {
		SchoolConfigRequest request = withBands(GradingMode.BOTH,
				new SchoolConfigRequest.GradeBand("A", 100, 0, null));

		assertThatThrownBy(() -> service.seedIfMissing(request))
				.isInstanceOf(ValidationException.class)
				.satisfies(thrown -> assertThat(((ValidationException) thrown).violations())
						.anySatisfy(violation -> assertThat(violation.field())
								.isEqualTo("gradingScheme.bands[0].minPercentage")));
	}

	@Test
	void repeatingAGradeLabelIsRejected() {
		SchoolConfigRequest request = withBands(GradingMode.BOTH,
				new SchoolConfigRequest.GradeBand("A", 51, 100, null),
				new SchoolConfigRequest.GradeBand("A", 0, 50, null));

		assertThatThrownBy(() -> service.seedIfMissing(request))
				.isInstanceOf(ValidationException.class)
				.satisfies(thrown -> assertThat(((ValidationException) thrown).violations())
						.anySatisfy(violation -> assertThat(violation.message()).contains("duplicates")));
	}

	@Test
	void gapsAreAllowedWhenOnlyMarksAreReported() {
		when(repository.existsById(SchoolConfig.SINGLETON_ID)).thenReturn(false);
		SchoolConfigRequest request = withBands(GradingMode.MARKS,
				new SchoolConfigRequest.GradeBand("A", 51, 100, null),
				new SchoolConfigRequest.GradeBand("B", 0, 40, null));

		assertThat(service.seedIfMissing(request)).isTrue();
	}

	@Test
	void narrowAccessorsReadFromTheStoredConfiguration() {
		when(repository.findById(SchoolConfig.SINGLETON_ID))
				.thenReturn(Optional.of(SchoolConfigFixtures.seeded(SCHOOL_CODE, NOW)));

		assertThat(service.receiptPrefix()).isEqualTo("TPS-RCP");
		assertThat(service.attendanceEditWindowHours()).isEqualTo(48);
		assertThat(service.workingDays()).hasSize(6);
		assertThat(service.gradingScheme().mode()).isEqualTo(GradingMode.BOTH);
		assertThat(service.identity().theme().primary()).isEqualTo("#0B3D91");
		assertThat(service.gradeFor(95).grade()).isEqualTo("A1");
		assertThat(service.gradeFor(0).grade()).isEqualTo("E");
	}

	private SchoolConfigRequest withBands(GradingMode mode, SchoolConfigRequest.GradeBand... bands) {
		SchoolConfigRequest request = SchoolConfigFixtures.validRequest();
		return new SchoolConfigRequest(request.identity(), request.landing(),
				new SchoolConfigRequest.GradingScheme(mode, List.of(bands)), request.academicSettings());
	}
}
