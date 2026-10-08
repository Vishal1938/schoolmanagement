package com.school.ai.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Ten chat requests a minute per IP, counted in memory.
 *
 * <p><strong>In memory on purpose, and only good enough on purpose.</strong> One deployment serves
 * one school from one process, so there is nothing to share a counter with; a restart forgives
 * everybody, which costs a stranger ten free questions and saves a Redis. A fixed window rather than
 * a sliding one, so a caller can in principle get twenty calls across a window boundary — the point
 * is to keep one visitor from spending the school's quota, not to be exact.
 *
 * <p>The IP comes from {@code HttpServletRequest#getRemoteAddr}, never from {@code X-Forwarded-For}:
 * a header a client sets is a header a client can change, which would turn the limit into an
 * invitation. Behind a reverse proxy, set {@code server.forward-headers-strategy} so that
 * {@code getRemoteAddr} is the real client — without it every visitor shares one bucket and the
 * limit is school-wide rather than per person.
 */
@Component
public class ChatRateLimiter {

	private static final Duration WINDOW = Duration.ofMinutes(1);
	private static final int LIMIT = 10;

	/** Beyond this many tracked IPs the map is swept of expired windows before the next insert. */
	private static final int SWEEP_ABOVE = 1_000;

	private final Map<String, Window> windows = new ConcurrentHashMap<>();
	private final Clock clock;

	public ChatRateLimiter(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Counts one request against {@code ip}.
	 *
	 * @throws AppException 429 {@code RATE_LIMITED}, carrying {@code retryAfterSeconds}, once the
	 *                      caller has used the window up
	 */
	public void check(String ip) {
		Instant now = Instant.now(clock);
		String key = ip == null || ip.isBlank() ? "unknown" : ip;

		if (windows.size() > SWEEP_ABOVE) {
			windows.values().removeIf(window -> window.hasExpired(now));
		}

		Window window = windows.compute(key, (unused, current) ->
				current == null || current.hasExpired(now) ? new Window(now) : current);
		if (window.count().incrementAndGet() > LIMIT) {
			long retryAfter = Math.max(1, Duration.between(now, window.expiresAt()).toSeconds());
			throw new AppException(ErrorType.RATE_LIMITED,
					"Too many questions in the last minute. Wait a moment and ask again.",
					Map.of("retryAfterSeconds", retryAfter), null);
		}
	}

	private record Window(Instant startedAt, AtomicInteger count) {

		Window(Instant startedAt) {
			this(startedAt, new AtomicInteger());
		}

		Instant expiresAt() {
			return startedAt.plus(WINDOW);
		}

		boolean hasExpired(Instant now) {
			return !now.isBefore(expiresAt());
		}
	}
}
