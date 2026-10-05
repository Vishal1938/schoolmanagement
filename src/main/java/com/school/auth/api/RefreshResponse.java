package com.school.auth.api;

import com.school.auth.app.LoginResult;

/**
 * The refresh response from API_CONTRACT.md §2: a new access token, and nothing else. The rotated refresh
 * token travels in the {@code Set-Cookie} header, and the {@code user} object is not repeated — the client
 * already has it from login, and {@code /auth/me} is there when it needs it again.
 */
public record RefreshResponse(String accessToken, long expiresIn) {

	public static RefreshResponse from(LoginResult result) {
		return new RefreshResponse(result.accessToken().value(), result.accessToken().expiresInSeconds());
	}
}
