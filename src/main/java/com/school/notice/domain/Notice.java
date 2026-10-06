package com.school.notice.domain;

import java.time.Instant;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A notice on the board.
 *
 * <p>{@code body} is <strong>plain text</strong>, with its line breaks kept and no markup of any
 * kind. Storing HTML would mean sanitizing it on the way in and trusting that sanitizer forever;
 * plain text has no such failure mode. The frontend must render it as text — {@code white-space:
 * pre-wrap} — and never as {@code innerHTML}.
 *
 * <p>A notice is visible from {@code publishAt} until {@code expiresAt}, so the office can write one
 * on Monday for Friday and have it disappear by itself afterwards. Neither the feed nor the public
 * feed ever returns a notice outside that window.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Notice.COLLECTION)
// The feed: every query filters on the publication window and sorts pinned first, newest next.
@CompoundIndex(name = "notices_feed_idx", def = "{'pinned': -1, 'publishAt': -1}")
// The audience filter the feed ANDs onto that window, per role.
@CompoundIndex(name = "notices_audience_idx", def = "{'audience': 1, 'publishAt': -1}")
// The public feed, which is unauthenticated and therefore the one most worth indexing.
@CompoundIndex(name = "notices_public_idx", def = "{'isPublic': 1, 'publishAt': -1}")
// A student's class notices.
@CompoundIndex(name = "notices_class_idx", def = "{'classId': 1, 'section': 1, 'publishAt': -1}")
// A teacher's own class notices.
@CompoundIndex(name = "notices_author_idx", def = "{'authorUniqueId': 1, 'publishAt': -1}")
public class Notice {

	public static final String COLLECTION = "notices";

	@Id
	private String id;

	private String title;

	/** Plain text; see the class comment. */
	private String body;

	private NoticeAudience audience;

	/** The class this is for, set only when {@code audience} is CLASS. */
	private String classId;

	/** One section of that class, upper-case. Null means the whole class. */
	private String section;

	/**
	 * Whether it also appears on the landing page, to visitors who are not logged in.
	 *
	 * <p>Named {@code publiclyVisible} in Java on purpose: a boolean field called {@code isPublic}
	 * gives Lombok the getter {@code isPublic()}, whose JavaBeans property name is {@code public} and
	 * no longer matches the field — the sort of mismatch that ends up storing two keys. The stored key
	 * and the JSON name are both {@code isPublic}, as the contract says.
	 */
	@Field("isPublic")
	private boolean publiclyVisible;

	/** Pinned notices sort above the rest, whatever their date. */
	private boolean pinned;

	private List<NoticeAttachment> attachments;

	/** When it becomes visible. Defaults to the moment it was created. */
	private Instant publishAt;

	/** When it stops being visible. Null means it never expires. */
	private Instant expiresAt;

	private String authorUniqueId;

	/** Copied at write time, so the board still names the author after they leave the school. */
	private String authorName;

	private Instant createdAt;

	private Instant updatedAt;
}
