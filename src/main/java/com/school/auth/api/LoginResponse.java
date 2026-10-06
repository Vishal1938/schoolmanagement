package com.school.auth.api;

import com.school.auth.app.LoginResult;

/**
 * The login response from API_CONTRACT.md §2.
 *
 * @param accessToken sent back as {@code Authorization: Bearer <accessToken>}
 * @param expiresIn   seconds the access token is valid for, so the client can refresh before it lapses
 */
public record LoginResponse(String accessToken, long expiresIn, AuthUserResponse user) {

	public static LoginResponse from(LoginResult result) {
		return new LoginResponse(
				result.accessToken().value(),
				result.accessToken().expiresInSeconds(),
				AuthUserResponse.from(result.user()));
	}
}
