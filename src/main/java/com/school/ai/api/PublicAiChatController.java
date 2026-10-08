package com.school.ai.api;

import com.school.ai.app.ChatRateLimiter;
import com.school.ai.app.PublicChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The landing page's FAQ bot, open to anyone: {@code /public/**} is permit-all in
 * {@code SecurityConfig}.
 *
 * <p>Unauthenticated and spending the school's provider quota, so it is the one endpoint in the
 * application with a rate limit: ten a minute per IP, then 429 {@code RATE_LIMITED}. The limit is
 * counted before the provider is called and after the body has been validated, so a well-formed
 * question costs a visitor one of their ten and a malformed one costs them nothing.
 *
 * <p>404 when {@code app.features.ai} is off, like every other AI route.
 */
@RestController
@RequestMapping("/public/ai")
@Tag(name = "Public", description = "Endpoints an anonymous visitor may call")
public class PublicAiChatController {

	private final PublicChatService chat;
	private final ChatRateLimiter rateLimiter;

	public PublicAiChatController(PublicChatService chat, ChatRateLimiter rateLimiter) {
		this.chat = chat;
		this.rateLimiter = rateLimiter;
	}

	@PostMapping("/chat")
	@SecurityRequirements
	@Operation(summary = "Ask a question about the school",
			description = "Answers from the school's public configuration and current public notices "
					+ "only, and says to contact the office rather than guessing. Nothing is stored. "
					+ "Ten requests a minute per IP.")
	public AiChatResponse chat(@Valid @RequestBody AiChatRequest request, HttpServletRequest httpRequest) {
		rateLimiter.check(httpRequest.getRemoteAddr());
		return chat.reply(request);
	}
}
