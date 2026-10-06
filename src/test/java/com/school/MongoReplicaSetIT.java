package com.school;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the application against a real single-node MongoDB replica set, the same topology docker
 * compose provides, and proves the two things later tasks depend on: the health probe reports UP,
 * and multi-document transactions commit and roll back.
 *
 * <p>Requires a running Docker daemon; it is bound to the {@code verify} phase, not {@code test}.
 */
// Seeding off: this test is about the replica set and transactions, and nothing here needs a seeded
// database. It also keeps B2's BootstrapAdminInitializer out of startup — against a fresh container it
// finds no ADMIN, and with no BOOTSTRAP_ADMIN_* configured it deliberately refuses to start.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
// Skipped instead of failed on a machine without a Docker daemon; CI always has one, so it runs there.
@Testcontainers(disabledWithoutDocker = true)
class MongoReplicaSetIT {

	private static final String COLLECTION = "transaction_probe";

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	MongoTemplate mongoTemplate;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Test
	void healthIsUpWithMongoConnected() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void multiDocumentTransactionsCommitAndRollBack() {
		mongoTemplate.remove(new Query(), COLLECTION);

		transactionTemplate.executeWithoutResult(status -> {
			mongoTemplate.insert(Map.of("probe", "committed-1"), COLLECTION);
			mongoTemplate.insert(Map.of("probe", "committed-2"), COLLECTION);
		});
		assertThat(mongoTemplate.count(new Query(), COLLECTION)).isEqualTo(2);

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			mongoTemplate.insert(Map.of("probe", "rolled-back"), COLLECTION);
			throw new IllegalStateException("forced rollback");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(mongoTemplate.count(Query.query(Criteria.where("probe").is("rolled-back")), COLLECTION)).isZero();
		assertThat(mongoTemplate.count(new Query(), COLLECTION)).isEqualTo(2);
	}
}
