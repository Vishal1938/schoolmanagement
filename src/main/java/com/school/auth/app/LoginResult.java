package com.school.auth.app;

import com.school.common.jwt.AccessToken;
import com.school.common.security.AuthPrincipal;

/**
 * What a successful login or password change produced: the signed access token, the caller it belongs to,
 * and the raw refresh token the controller puts in the cookie.
 *
 * @param refreshToken the only place the raw value ever appears outside the {@code Set-Cookie} header.
 *                     The database holds its digest, and it is never logged or audited
 */
public record LoginResult(AccessToken accessToken, AuthPrincipal user, String refreshToken) {
}
