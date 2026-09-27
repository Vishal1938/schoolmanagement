package com.school.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Enables {@code @Transactional} against MongoDB. Spring Boot does not register a transaction
 * manager for MongoDB on its own, and several flows require one: creating a student together with
 * its user account (B5), and capturing a payment together with invoice updates and the receipt
 * number (B12).
 *
 * <p>This is why MongoDB must run as a replica set, even locally as a single node: standalone
 * {@code mongod} rejects multi-document transactions.
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
public class MongoTransactionConfig {

	@Bean
	public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
		return new MongoTransactionManager(databaseFactory);
	}
}
