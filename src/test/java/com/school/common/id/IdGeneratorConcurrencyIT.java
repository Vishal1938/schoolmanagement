package com.school.common.id;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generator's whole point: 200 IDs requested at once against a real MongoDB produce 200 different
 * IDs. A read-then-write generator passes a single-threaded test and fails this one.
 *
 * <p>Requires a running Docker daemon; bound to the {@code verify} phase, not {@code test}.
 */
@SpringBootTest(properties = {"app.school-code=ITS", "app.seed.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
class IdGeneratorConcurrencyIT {

	private static final int ATTEMPTS = 200;

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

	@Autowired
	IdGenerator generator;

	@Autowired
	MongoTemplate mongoTemplate;

	@Test
	void twoHundredParallelGenerationsProduceNoDuplicates() throws Exception {
		List<Callable<String>> tasks = IntStream.range(0, ATTEMPTS)
				.<Callable<String>>mapToObj(i -> () -> generator.next(IdType.STU, 2026))
				.toList();

		List<String> ids;
		try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
			List<Future<String>> futures = pool.invokeAll(tasks);
			ids = new java.util.ArrayList<>(futures.size());
			for (Future<String> future : futures) {
				ids.add(future.get());
			}
		}

		assertThat(ids).hasSize(ATTEMPTS);
		assertThat(Set.copyOf(ids)).hasSize(ATTEMPTS);
		assertThat(ids).allMatch(id -> id.matches("ITS-STU-26-\\d{5,}"));

		// The sequence is dense: 1..200 were all handed out, none skipped and none repeated.
		Set<String> expected = IntStream.rangeClosed(1, ATTEMPTS)
				.mapToObj(sequence -> "ITS-STU-26-%05d".formatted(sequence))
				.collect(java.util.stream.Collectors.toSet());
		assertThat(Set.copyOf(ids)).isEqualTo(expected);

		Counter counter = mongoTemplate.findById("STU-26", Counter.class);
		assertThat(counter).isNotNull();
		assertThat(counter.getSeq()).isEqualTo(ATTEMPTS);
	}

	@Test
	void eachTypeAndYearHasItsOwnSequence() {
		mongoTemplate.remove(new Query(), Counter.COLLECTION);

		assertThat(generator.next(IdType.STU, 2026)).isEqualTo("ITS-STU-26-00001");
		assertThat(generator.next(IdType.EMP, 2026)).isEqualTo("ITS-EMP-26-0001");
		assertThat(generator.next(IdType.STU, 2027)).isEqualTo("ITS-STU-27-00001");
		assertThat(generator.next(IdType.STU, 2026)).isEqualTo("ITS-STU-26-00002");

		// One counter document per key, named for the type and the two-digit year.
		assertThat(mongoTemplate.findAll(Counter.class))
				.extracting(Counter::getId)
				.containsExactlyInAnyOrder("STU-26", "EMP-26", "STU-27");
	}

	@Test
	void receiptNumbersShareTheGeneratorWithoutDisturbingTheIdSequences() {
		mongoTemplate.remove(new Query(), Counter.COLLECTION);
		generator.next(IdType.STU, 2026);

		assertThat(generator.nextNumber("DVM-RCP", 6, 2026)).isEqualTo("DVM-RCP-26-000001");
		assertThat(generator.nextNumber("DVM-RCP", 6, 2026)).isEqualTo("DVM-RCP-26-000002");
		assertThat(generator.next(IdType.STU, 2026)).isEqualTo("ITS-STU-26-00002");
	}

	/**
	 * IDs inserted by hand do not break the generator: bumping the counter with {@code $max} makes it
	 * resume above them, which is the documented recovery step.
	 */
	@Test
	void aCounterCanBeFastForwardedPastPreExistingIds() {
		mongoTemplate.remove(new Query(), Counter.COLLECTION);
		mongoTemplate.upsert(new Query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is("EMP-26")),
				new org.springframework.data.mongodb.core.query.Update().max("seq", 12L), Counter.class);

		assertThat(generator.next(IdType.EMP, 2026)).isEqualTo("ITS-EMP-26-0013");
	}
}
