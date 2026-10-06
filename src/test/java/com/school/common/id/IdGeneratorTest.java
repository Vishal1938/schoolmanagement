package com.school.common.id;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.school.common.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ID shapes and counter keys. The real atomicity is covered by {@code IdGeneratorConcurrencyIT}. */
class IdGeneratorTest {

	/** 1 April 2026, so the two-digit year is 26 and the school session has just started. */
	private static final Instant NOW = Instant.parse("2026-04-01T09:30:00Z");

	private MongoOperations mongo;
	private IdGenerator generator;

	@BeforeEach
	void setUp() {
		mongo = mock(MongoOperations.class);
		generator = new IdGenerator(mongo, properties("DEMO"), Clock.fixed(NOW, ZoneId.of("Asia/Kolkata")));
	}

	@Test
	void studentIdsUseAFiveDigitSequence() {
		counterReturns(1);

		assertThat(generator.next(IdType.STU)).isEqualTo("DEMO-STU-26-00001");
	}

	@Test
	void employeeIdsUseAFourDigitSequence() {
		counterReturns(7);

		assertThat(generator.next(IdType.EMP)).isEqualTo("DEMO-EMP-26-0007");
	}

	@Test
	void aSequenceWiderThanItsPaddingIsNotTruncated() {
		counterReturns(123_456);

		assertThat(generator.next(IdType.EMP)).isEqualTo("DEMO-EMP-26-123456");
	}

	@Test
	void theCounterKeyIsTheTypeAndTheTwoDigitYear() {
		counterReturns(1);

		generator.next(IdType.STU);

		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		verify(mongo).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class),
				eq(Counter.class));
		assertThat(query.getValue().getQueryObject().get("_id")).isEqualTo("STU-26");
	}

	@Test
	void theCounterIsIncrementedWithAnUpsertThatReturnsTheNewValue() {
		counterReturns(1);

		generator.next(IdType.EMP);

		ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
		ArgumentCaptor<FindAndModifyOptions> options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
		verify(mongo).findAndModify(any(Query.class), update.capture(), options.capture(), eq(Counter.class));
		assertThat(update.getValue().getUpdateObject().toJson()).contains("$inc").contains("seq");
		assertThat(options.getValue().isUpsert()).isTrue();
		assertThat(options.getValue().isReturnNew()).isTrue();
	}

	@Test
	void anExplicitYearIsUsedForBackdatedAdmissions() {
		counterReturns(42);

		assertThat(generator.next(IdType.STU, 2024)).isEqualTo("DEMO-STU-24-00042");
		assertThat(IdGenerator.counterKey("STU", 2024)).isEqualTo("STU-24");
	}

	@Test
	void everyYearStartsItsOwnSequence() {
		AtomicLong sequence = new AtomicLong();
		when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
				eq(Counter.class))).thenAnswer(invocation -> counter(sequence.incrementAndGet()));

		// Different keys in the database; the generator only ever asks for "the next one for this key".
		assertThat(generator.next(IdType.STU, 2026)).isEqualTo("DEMO-STU-26-00001");
		assertThat(generator.next(IdType.STU, 2027)).isEqualTo("DEMO-STU-27-00002");
	}

	@Test
	void receiptNumbersReuseTheSameCounterMachinery() {
		counterReturns(1);

		assertThat(generator.nextNumber("DVM-RCP", 6)).isEqualTo("DVM-RCP-26-000001");

		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		verify(mongo).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class),
				eq(Counter.class));
		assertThat(query.getValue().getQueryObject().get("_id")).isEqualTo("DVM-RCP-26");
	}

	@Test
	void aLowerCasePrefixIsNormalised() {
		counterReturns(3);

		assertThat(generator.nextNumber(" dvm-rcp ", 4)).isEqualTo("DVM-RCP-26-0003");
	}

	@Test
	void theSchoolCodeComesFromConfigurationAndIsNeverHardcoded() {
		generator = new IdGenerator(mongo, properties("SVM"), Clock.fixed(NOW, ZoneId.of("Asia/Kolkata")));
		counterReturns(9);

		assertThat(generator.next(IdType.STU)).startsWith("SVM-");
	}

	@Test
	void aCounterThatCannotBeIncrementedIsAnError() {
		when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
				eq(Counter.class))).thenReturn(null);

		assertThatThrownBy(() -> generator.next(IdType.STU))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("STU-26");
	}

	private void counterReturns(long sequence) {
		when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
				eq(Counter.class))).thenReturn(counter(sequence));
	}

	private Counter counter(long sequence) {
		return Counter.builder().id("test").seq(sequence).build();
	}

	private AppProperties properties(String schoolCode) {
		return new Binder(new MapConfigurationPropertySource(Map.of("app.school-code", schoolCode)))
				.bind("app", AppProperties.class)
				.get();
	}
}
