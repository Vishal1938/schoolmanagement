package com.school.common.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes an unreachable database fail quickly and legibly.
 *
 * <p>The driver's default server-selection timeout is 30 seconds, and because index creation happens
 * during context refresh, an unreachable MongoDB means the application spends half a minute retrying
 * before printing a bean-creation stack trace whose real cause is buried five {@code Caused by} levels
 * down. Five seconds is long enough for a slow Atlas handshake and short enough to read.
 *
 * <p>Common causes when this does fail: the client IP is not on the Atlas access list, a free-tier
 * cluster is paused, or docker compose is not running for a local URI. A TLS
 * {@code internal_error} alert specifically means the server rejected the connection before
 * authentication, so it is a network or access-list problem rather than a credential one.
 */
@Configuration(proxyBeanMethods = false)
public class MongoClientConfig {

	@Bean
	public MongoClientSettingsBuilderCustomizer mongoTimeouts() {
		return builder -> builder
				.applyToClusterSettings(cluster ->
						cluster.serverSelectionTimeout(5, TimeUnit.SECONDS))
				.applyToSocketSettings(socket -> socket
						.connectTimeout(5, TimeUnit.SECONDS)
						.readTimeout(30, TimeUnit.SECONDS));
	}
}
