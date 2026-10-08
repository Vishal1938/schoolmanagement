package com.school.ai.infra;

import com.school.ai.app.AiFeature;
import com.school.common.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Makes the 404 on a disabled AI route cover the whole route, not just the ones that remember to ask.
 *
 * <p>Each service calls {@code AiFeature.require()} itself, which is the check that matters. This
 * interceptor is in front of it for two reasons. It runs before {@code @PreAuthorize}, so a student
 * who pokes at {@code /ai/insights} in a deployment without AI gets the same 404 an administrator
 * does rather than a 403 that admits the route is there. And it applies to every path under
 * {@code /ai} and {@code /public/ai} by pattern, so an endpoint added later is gated whether or not
 * its author remembered to.
 *
 * <p>One case it cannot cover: an <em>unauthenticated</em> call to {@code /ai/**} is refused by the
 * security filter chain with 401 before any interceptor runs. That is the same answer every other
 * protected route gives to a request with no token, and nothing about AI is leaked by it.
 */
@Configuration(proxyBeanMethods = false)
public class AiFeatureGateConfig implements WebMvcConfigurer {

	private final AiFeature feature;
	private final String basePath;

	public AiFeatureGateConfig(AiFeature feature, AppProperties properties) {
		this.feature = feature;
		this.basePath = properties.apiBasePath();
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new FeatureGate(feature))
				.addPathPatterns(basePath + "/ai/**", basePath + "/public/ai/**");
	}

	/**
	 * Throws rather than writing a body: an exception out of {@code preHandle} goes through the same
	 * {@code GlobalExceptionHandler} as everything else, so the problem detail is built in one place.
	 */
	private record FeatureGate(AiFeature feature) implements HandlerInterceptor {

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
			feature.require();
			return true;
		}
	}
}
