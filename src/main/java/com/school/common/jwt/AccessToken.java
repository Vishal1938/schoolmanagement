package com.school.common.jwt;

/**
 * A freshly signed access token.
 *
 * @param value            the compact JWS, sent as {@code Authorization: Bearer <value>}
 * @param expiresInSeconds seconds until it expires, which is what the contract's {@code expiresIn} is
 */
public record AccessToken(String value, long expiresInSeconds) {
}
