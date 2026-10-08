package com.school.ai.infra;

import java.time.Duration;

import com.school.common.config.AppProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * The one chat model in the application, built by hand rather than by Spring AI's own
 * auto-configuration.
 *
 * <p><strong>Why by hand.</strong> {@code OpenAiChatAutoConfiguration} asserts a non-empty
 * {@code spring.ai.openai.api-key} while the context is being built, so a school that has AI
 * switched off would fail to start for want of a key it does not need. {@code spring.ai.model.chat}
 * is therefore pinned to {@code none} in {@code application.yml} and these beans exist only when
 * {@code app.features.ai} is true — which is also what makes the 404 on every {@code /ai} route
 * honest: with the feature off there is no model on the context at all.
 *
 * <p><strong>Two deliberate departures from the defaults.</strong> The request factory carries the
 * configured timeout, 30s, because the auto-configured {@code RestClient.Builder} has none and a
 * hung provider would otherwise hold a request open indefinitely. And the retry template is a single
 * attempt, where Spring AI's default is ten with exponential backoff: ten tries at 30s each is not a
 * timeout a user would call one, and an endpoint that answers 502 in half a minute is more use than
 * one that answers eventually.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.features", name = "ai", havingValue = "true")
public class AiModelConfig {

	@Bean
	public OpenAiApi openAiApi(AppProperties properties) {
		AppProperties.Ai ai = properties.ai();
		if (!ai.hasApiKey()) {
			// Fail at startup rather than on the first request: a deployment that advertises the
			// feature in GET /public/school and then 502s on every call is worse than one that does
			// not come up. The message names the variable and never the value.
			throw new IllegalStateException("app.features.ai is true but no AI_API_KEY is configured. "
					+ "Set AI_API_KEY, or set APP_FEATURES_AI=false to run without the AI features.");
		}
		return OpenAiApi.builder()
				.apiKey(ai.apiKey())
				.restClientBuilder(RestClient.builder().requestFactory(requestFactory(ai.timeout())))
				.build();
	}

	@Bean
	public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi, AppProperties properties) {
		return OpenAiChatModel.builder()
				.openAiApi(openAiApi)
				.defaultOptions(OpenAiChatOptions.builder()
						.model(properties.ai().model())
						// Low, not zero: these are quiz questions and report remarks, so a little
						// variety between two calls is wanted, and invention is not.
						.temperature(0.3)
						.build())
				.retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
				.build();
	}

	@Bean
	public ChatClient aiChatClient(OpenAiChatModel chatModel) {
		// No default system prompt: each of the four features sets its own, and they have nothing in
		// common beyond being about this school.
		return ChatClient.builder(chatModel).build();
	}

	/**
	 * Connect and read timeouts on the provider call. Both the same: a provider that has not accepted
	 * the connection within the budget is no more use than one that has not answered within it.
	 */
	private static org.springframework.http.client.ClientHttpRequestFactory requestFactory(Duration timeout) {
		return ClientHttpRequestFactoryBuilder.detect()
				.build(ClientHttpRequestFactorySettings.defaults().withTimeouts(timeout, timeout));
	}
}
