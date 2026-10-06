package com.school.common.audit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Turns an entity into the map stored in an audit entry, with anything that looks like a secret
 * replaced by {@link #REDACTED}.
 *
 * <p>This runs centrally, on the way in, rather than relying on every caller to hand over a clean
 * object. {@code AuditService.record(action, type, id, before, after)} is therefore safe to call with a
 * whole {@code User}: the password hash will not reach the database. Callers cannot opt out, which is
 * the point — CLAUDE.md forbids storing or logging credential material, and an audit trail is read by
 * more people than the code that writes it.
 *
 * <p>Matching is by substring on the field name, case-insensitively, and applies at every depth,
 * inside nested objects and inside lists. {@code hash} is on the list even though it is broad: a field
 * innocently named something like {@code hashtag} being shown as redacted in an audit entry costs
 * nothing, while one named {@code passwordHash} slipping through costs a great deal.
 */
@Component
public class AuditSanitizer {

	/** What a redacted value is replaced with, so the entry shows that something was removed. */
	public static final String REDACTED = "[redacted]";

	// "accountnumber" covers the employee bank details in B6. The stored value is already AES-GCM
	// ciphertext, so this is defence in depth rather than the only thing protecting it.
	private static final Set<String> SENSITIVE_FRAGMENTS = Set.of(
			"password", "passwd", "secret", "token", "credential", "hash", "otp",
			"apikey", "accesskey", "privatekey", "authorization", "cookie", "sessionid",
			"accountnumber");

	private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

	private final ObjectMapper objectMapper;

	public AuditSanitizer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * Converts an entity to a map and redacts its sensitive fields.
	 *
	 * @return null when {@code value} is null, so "no previous state" stays distinguishable from "empty
	 *         previous state"
	 */
	public Map<String, Object> sanitize(Object value) {
		if (value == null) {
			return null;
		}
		Map<String, Object> asMap = value instanceof Map<?, ?> map
				? toStringKeyedMap(map)
				: objectMapper.convertValue(value, MAP);
		return redactMap(asMap);
	}

	private Map<String, Object> toStringKeyedMap(Map<?, ?> map) {
		Map<String, Object> copy = new LinkedHashMap<>();
		map.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
		return copy;
	}

	private Map<String, Object> redactMap(Map<String, Object> source) {
		Map<String, Object> result = new LinkedHashMap<>();
		source.forEach((key, value) -> result.put(key, isSensitive(key) ? REDACTED : redactValue(value)));
		return result;
	}

	private Object redactValue(Object value) {
		if (value instanceof Map<?, ?> map) {
			return redactMap(toStringKeyedMap(map));
		}
		if (value instanceof Collection<?> collection) {
			List<Object> items = new ArrayList<>(collection.size());
			collection.forEach(item -> items.add(redactValue(item)));
			return items;
		}
		return value;
	}

	private boolean isSensitive(String fieldName) {
		String lower = fieldName.toLowerCase(Locale.ROOT);
		return SENSITIVE_FRAGMENTS.stream().anyMatch(lower::contains);
	}
}
