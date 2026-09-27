package com.school.common.security;

import com.school.common.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Baseline security: stateless, deny-by-default, with only the health probe, the OpenAPI endpoints
 * and the {@code /public/**} routes open. Authentication itself (JWT filter, refresh cookies, the
 * {@code mustChangePassword} gate) is added in B2; until then there are no authenticated endpoints,
 * so every non-public path answers 401.
 *
 * <p>Authorization is always enforced here and in {@code @PreAuthorize} on permissions — never on
 * role names, and never in the frontend.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

	private final AppProperties properties;

	public SecurityConfig(AppProperties properties) {
		this.properties = properties;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http,
			ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
			ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
		String publicPattern = properties.apiBasePath() + "/public/**";
		return http
				// No browser-session state and no cookie-authenticated mutations, so CSRF tokens add
				// nothing; the refresh cookie added in B2 is SameSite=Strict and path-scoped.
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.anonymous(Customizer.withDefaults())
				.headers(headers -> headers
						.frameOptions(frame -> frame.deny())
						.xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
						.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
						.permitAll()
						// Landing page, public notices and the payment webhook.
						.requestMatchers(publicPattern).permitAll()
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.build();
	}

	/** BCrypt at strength 12, as required for every stored password. */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}
}
