package com.school.schoolconfig.api;

import com.school.common.config.AppProperties;
import com.school.schoolconfig.app.ModuleGate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link ModuleGateInterceptor} across the whole API.
 *
 * <p>It lives in this module rather than in {@code common.config} beside {@code WebConfig}: the
 * interceptor needs {@code ModuleGate}, and {@code common} is the module every other one depends on,
 * so a reference from there back to {@code schoolconfig} would be a cycle Spring Modulith rejects.
 *
 * <p>{@link ObjectProvider} rather than the bean itself, because a {@code WebMvcConfigurer} is built
 * very early: asking for {@code ModuleGate} here would pull {@code SchoolConfigService} and the
 * Mongo repository infrastructure into MVC's own initialisation. The gate is resolved on the first
 * request instead, by which time everything exists.
 */
@Configuration(proxyBeanMethods = false)
public class ModuleGateConfig implements WebMvcConfigurer {

	private final ObjectProvider<ModuleGate> gate;
	private final AppProperties properties;

	public ModuleGateConfig(ObjectProvider<ModuleGate> gate, AppProperties properties) {
		this.gate = gate;
		this.properties = properties;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// Mapped to everything; the interceptor's own table decides what is actually gated, so the
		// patterns live in one place rather than being split across here and there.
		registry.addInterceptor(new ModuleGateInterceptor(gate, properties)).addPathPatterns("/**");
	}
}
