package com.school.ai.infra;

import java.util.List;

import com.school.common.config.AppProperties;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The only place in the application that talks to the AI provider.
 *
 * <p><strong>Every failure is a 502.</strong> A provider that is down, slow, out of quota or
 * answering with something that will not parse is an upstream problem and not a fault in this
 * request, so none of it is allowed to surface as a 500. The client-facing detail is fixed text: the
 * provider's own message is useful in a log and is not something to hand to a browser.
 *
 * <p><strong>Nothing logged here can contain the key.</strong> The provider's messages carry a URL
 * and a status, never the {@code Authorization} header, but the scrub below is unconditional anyway —
 * rule 9 is not a thing to be right about by accident.
 *
 * <p>The {@link ChatClient} is resolved through an {@link ObjectProvider} because it only exists when
 * {@code app.features.ai} is true. This bean exists either way, so a controller can be wired without
 * every one of them having to be conditional; it is {@code AiFeature} that decides whether a request
 * gets as far as asking.
 */
@Component
public class AiGateway {

	private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

	private final ObjectProvider<ChatClient> chatClient;
	private final String apiKey;

	public AiGateway(ObjectProvider<ChatClient> chatClient, AppProperties properties) {
		this.chatClient = chatClient;
		this.apiKey = properties.ai().apiKey();
	}

	/**
	 * Asks for a structured answer and maps it onto {@code type}.
	 *
	 * <p>Spring AI appends the JSON schema of {@code type} to the user message and parses the reply
	 * back, so the shape is the provider's problem rather than ours. What the fields <em>contain</em>
	 * is still ours: a reply that parses can still be nonsense, which is why every caller validates.
	 *
	 * @param what a few words naming the call, for the log line when it fails
	 */
	public <T> T entity(String what, String system, String user, Class<T> type) {
		return call(what, () -> chat().prompt().system(system).user(user).call().entity(type));
	}

	/**
	 * Asks for plain prose.
	 *
	 * @param history earlier turns of the conversation, oldest first; the new {@code user} message is
	 *                appended after them
	 */
	public String text(String what, String system, List<Message> history, String user) {
		return call(what, () -> chat().prompt()
				.system(system)
				// Pre-built messages, not templated text: history and school content are arbitrary
				// strings, and a stray brace in one would otherwise be read as a prompt placeholder.
				.messages(history)
				.user(user)
				.call()
				.content());
	}

	private ChatClient chat() {
		ChatClient client = chatClient.getIfAvailable();
		if (client == null) {
			// Only reachable if a caller skipped AiFeature.require(), so it is a bug rather than a
			// configuration state — but a 502 is still a truer answer than a 500.
			throw new AppException(ErrorType.DEPENDENCY_FAILED, "The AI provider is not configured");
		}
		return client;
	}

	private <T> T call(String what, Call<T> call) {
		try {
			T answer = call.run();
			if (answer == null) {
				throw new IllegalStateException("the provider returned an empty response");
			}
			return answer;
		}
		catch (AppException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			log.warn("The AI provider could not {}: {}", what, scrub(ex.getMessage()));
			throw new AppException(ErrorType.DEPENDENCY_FAILED,
					"The AI service could not " + what + " just now. Try again in a moment.", ex);
		}
	}

	private String scrub(String message) {
		if (message == null) {
			return "no message";
		}
		return apiKey == null || apiKey.isBlank() ? message : message.replace(apiKey, "[redacted]");
	}

	@FunctionalInterface
	private interface Call<T> {

		T run();
	}
}
