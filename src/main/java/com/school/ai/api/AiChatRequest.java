package com.school.ai.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A question for the landing-page bot, for {@code POST /public/ai/chat}.
 *
 * <p>Unauthenticated, so both limits are hard: 500 characters a message and six turns of history.
 * The server keeps no conversation of its own — there is nobody to key one to — so the client sends
 * back whatever context it wants the bot to have, and the length of that context is therefore the
 * client's to spend and the server's to cap.
 *
 * @param history earlier turns, oldest first, six at the most. Anything longer is 400 rather than
 *                silently truncated: a bot answering half a conversation it was not told it was only
 *                given half of is worse than an error the client can fix
 */
public record AiChatRequest(
		@NotBlank @Size(max = 500) String message,
		@Size(max = 6) List<@Valid Turn> history) {

	/**
	 * One earlier turn.
	 *
	 * @param role    who said it
	 * @param content what was said. Capped like {@code message}, and an assistant turn is capped the
	 *                same way because it is a client-supplied string either way — nothing stops a
	 *                caller inventing one, which is also why the system prompt is the only thing the
	 *                bot takes its instructions from
	 */
	public record Turn(
			@NotNull Role role,
			@NotBlank @Size(max = 500) String content) {

		public enum Role {

			/** The visitor. */
			USER,

			/** The bot's own earlier reply. */
			ASSISTANT
		}
	}
}
