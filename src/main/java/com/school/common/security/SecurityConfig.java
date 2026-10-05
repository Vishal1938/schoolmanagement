package com.school.common.security;

import com.school.common.config.AppProperties;
import com.school.common.jwt.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Stateless, deny-by-default security. Open to everyone: the health probe, the OpenAPI endpoints, the
 * {@code /public/**} routes and the two unauthenticated auth calls (login, logout). Everything else needs
 * a valid access token, which {@link JwtAuthenticationFilter} turns into an {@link AuthPrincipal}.
 *
 * <p>Authorization is always enforced here and in {@code @PreAuthorize} on permissions — never on
 * role names, and never in the frontend.
 *
 * <p>Refresh-token rotation and the {@code mustChangePassword} gate arrive in B2 part 2.
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
			ProblemDetailAccessDeniedHandler accessDeniedHandler,
			JwtService jwtService,
			ProblemDetailResponseWriter problemWriter) throws Exception {
		String basePath = properties.apiBasePath();
		String publicPattern = basePath + "/public/**";
		return http
				// No browser-session state and no cookie-authenticated mutations, so CSRF tokens add
				// nothing; the refresh cookie added in B2 is SameSite=Strict and path-scoped.
				.csrf(csrf -> csrf.disable())
				// Frontend and backend are same-origin in production (Nginx) and behind the Vite dev proxy
				// locally, so no request we serve is ever cross-origin.
				.cors(AbstractHttpConfigurer::disable)
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
						// None of these three can require a valid access token: login is how you get one,
						// refresh is what you call once it has expired, and logout has to work either way.
						// They authenticate themselves — by password, or by the refresh cookie.
						.requestMatchers(HttpMethod.POST, basePath + "/auth/login", basePath + "/auth/refresh",
								basePath + "/auth/logout")
						.permitAll()
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(new JwtAuthenticationFilter(jwtService, problemWriter),
						UsernamePasswordAuthenticationFilter.class)
				// After the JWT filter, which is what puts the principal it inspects in place.
				.addFilterAfter(new PasswordChangeRequiredFilter(basePath, problemWriter),
						JwtAuthenticationFilter.class)
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
