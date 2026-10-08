package com.school.ai.api;

/**
 * The bot's answer.
 *
 * <p>Nothing is stored: no transcript, no visitor, no id. The client holds the conversation and
 * sends the part of it that matters back in {@code history}.
 */
public record AiChatResponse(String reply) {
}
