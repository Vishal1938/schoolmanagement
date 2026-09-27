package com.school.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application's {@link Clock}. Inject it everywhere instead of calling
 * {@code LocalDate.now()} or {@code Instant.now()}, so due dates, attendance windows, lockouts and
 * receipts can be tested deterministically with a fixed clock.
 *
 * <p>The zone comes from {@code app.timezone} (default {@code Asia/Kolkata}) rather than the host's
 * default, so a container in another region still rolls the date over at the school's midnight.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	@ConditionalOnMissingBean
	public Clock clock(AppProperties properties) {
		return Clock.system(ZoneId.of(properties.timezone()));
	}
}
