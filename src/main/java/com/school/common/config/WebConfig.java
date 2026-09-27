package com.school.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.HandlerTypePredicate;

/**
 * Puts every controller under {@code app.api-base-path} (default {@code /api/v1}) without using a
 * servlet context path, which keeps {@code /actuator/health}, {@code /v3/api-docs} and
 * {@code /swagger-ui.html} at the root where the contract and the deployment probes expect them.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	private final AppProperties properties;

	public WebConfig(AppProperties properties) {
		this.properties = properties;
	}

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(properties.apiBasePath(), HandlerTypePredicate.forBasePackage("com.school"));
	}
}
