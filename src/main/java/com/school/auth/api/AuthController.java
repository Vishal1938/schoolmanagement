package com.school.auth.api;

import java.time.Duration;

import com.school.auth.app.AuthService;
import com.school.auth.app.LoginResult;
import com.school.common.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in, token refresh, the current user, password change and sign-out.
 *
 * <p>The refresh token only ever travels in an {@code HttpOnly} cookie scoped to this controller's path, so
 * no script can read it and it is not attached to any other request. The access token, by contrast, is in
 * the response body: it is short-lived, and the client needs to put it in a header.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Login, refresh, current user, password change and logout")
public class AuthController {

	static final String REFRESH_COOKIE = "refreshToken";

	private final AuthService authService;
	private final AppProperties properties;

	public AuthController(AuthService authService, AppProperties properties) {
		this.authService = authService;
		this.properties = properties;
	}

	@PostMapping("/login")
	@SecurityRequirements
	@Operation(summary = "Log in with a uniqueId and password",
			description = "Returns an access token valid for 15 minutes plus the user's permission list, and "
					+ "sets the refresh cookie. Wrong credentials answer 401 INVALID_CREDENTIALS without "
					+ "saying which half was wrong; five wrong passwords in a row answer 423 LOCKED for the "
					+ "next 15 minutes.")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResult result = authService.login(request.uniqueId(), request.password());
		return withRefreshCookie(result).body(LoginResponse.from(result));
	}

	@PostMapping("/refresh")
	@SecurityRequirements
	@Operation(summary = "Exchange the refresh cookie for a new access token",
			description = "Rotates the refresh token: the cookie sent with this request stops working. "
					+ "Presenting an already-rotated token revokes every token from that login and answers "
					+ "401 TOKEN_INVALID.")
	public ResponseEntity<RefreshResponse> refresh(
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		LoginResult result = authService.refresh(refreshToken);
		return withRefreshCookie(result).body(RefreshResponse.from(result));
	}

	@GetMapping("/me")
	@Operation(summary = "The authenticated user",
			description = "Read from the database rather than the token, so a change to the account shows up "
					+ "immediately.")
	public AuthUserResponse me() {
		return AuthUserResponse.from(authService.currentUser());
	}

	@PostMapping("/change-password")
	@Operation(summary = "Change your own password",
			description = "Clears mustChangePassword and ends every other session this account has. Returns a "
					+ "new access token and a new refresh cookie, because the token used to call this one "
					+ "still says a change is pending — use the pair from this response from here on.")
	public ResponseEntity<LoginResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		LoginResult result = authService.changePassword(request.currentPassword(), request.newPassword());
		return withRefreshCookie(result).body(LoginResponse.from(result));
	}

	@PostMapping("/logout")
	@SecurityRequirements
	@Operation(summary = "Log out",
			description = "Deletes the refresh token and every token rotated from the same login, and clears "
					+ "the cookie. The access token is not revocable by design and simply expires, so the "
					+ "client must drop it too.")
	public ResponseEntity<Void> logout(
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		authService.logout(refreshToken);
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
				.build();
	}

	private ResponseEntity.BodyBuilder withRefreshCookie(LoginResult result) {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE,
						refreshCookie(result.refreshToken(), properties.jwt().refreshTtl()).toString());
	}

	/**
	 * The refresh cookie. {@code Max-Age=0} with an empty value clears it; the other attributes have to
	 * match the ones it was set with, above all the path, or a browser keeps the original alongside it.
	 *
	 * <p>{@code SameSite=Strict} means the cookie is not sent on any cross-site navigation, which is what
	 * makes a CSRF token unnecessary on the refresh endpoint.
	 */
	private ResponseCookie refreshCookie(String value, Duration maxAge) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
				.httpOnly(true)
				.secure(properties.jwt().cookieSecure())
				.sameSite("Strict")
				.path(properties.jwt().cookiePath())
				.maxAge(maxAge)
				.build();
	}
}
