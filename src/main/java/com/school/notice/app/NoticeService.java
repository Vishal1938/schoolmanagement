package com.school.notice.app;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.SchoolClass;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.notice.api.NoticeRequest;
import com.school.notice.domain.Notice;
import com.school.notice.domain.NoticeAttachment;
import com.school.notice.domain.NoticeAudience;
import com.school.notice.infra.NoticeRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * The notice board.
 *
 * <p>Two rules are enforced here rather than in an annotation, because both depend on the notice
 * rather than on the endpoint.
 *
 * <p><strong>Reading is filtered, not refused.</strong> Everybody may call the feed; what comes back
 * is what their role is addressed by, inside the publication window. The filter is a database query
 * and not a pass over the results, so a notice a caller may not see is never loaded in the first
 * place.
 *
 * <p><strong>Writing is narrowed by permission.</strong> {@code NOTICE_WRITE_ALL} (an admin) may
 * write anything; {@code NOTICE_WRITE_CLASS} (a teacher) may only write class notices, may not flag
 * them public or pinned, and may only touch their own.
 */
@Service
public class NoticeService {

	private static final String AUDIT_ENTITY = "Notice";

	/**
	 * Pinned first, then newest. Fixed rather than taken from the request: the board has one order,
	 * and a client that could sort it differently would quietly bury a pinned notice.
	 */
	private static final Sort FEED_SORT = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("publishAt"));

	private final NoticeRepository notices;
	private final MongoOperations mongo;
	private final SchoolClassService classes;
	private final StudentService students;
	private final NoticeAttachmentService attachments;
	private final AuditService audit;
	private final Clock clock;

	public NoticeService(NoticeRepository notices, MongoOperations mongo, SchoolClassService classes,
			StudentService students, NoticeAttachmentService attachments, AuditService audit, Clock clock) {
		this.notices = notices;
		this.mongo = mongo;
		this.classes = classes;
		this.students = students;
		this.attachments = attachments;
		this.audit = audit;
		this.clock = clock;
	}

	// --- reading ----------------------------------------------------------------------------------

	/** The caller's board: published, unexpired, addressed to them. Pinned first, then newest. */
	public Page<Notice> feed(Pageable pageable) {
		AuthPrincipal caller = CurrentUser.require();
		Instant now = Instant.now(clock);
		return page(new Criteria().andOperator(withinWindow(now), addressedTo(caller)), pageable);
	}

	/** The landing page's board: public notices only, same window, same order. */
	public Page<Notice> publicFeed(Pageable pageable) {
		Instant now = Instant.now(clock);
		return page(new Criteria().andOperator(withinWindow(now), Criteria.where("isPublic").is(true)), pageable);
	}

	/**
	 * The most recent public notices, reduced to what another module may read (B18).
	 *
	 * <p>No caller check: every one of these is already published to the internet by
	 * {@code GET /public/notices}, so there is nothing here an anonymous visitor could not fetch
	 * themselves. Pinned first then newest, the same order the landing page shows.
	 *
	 * @param limit how many to take, at most 20 — this ends up in a prompt, not on a page
	 */
	public List<PublicNoticeBrief> latestPublic(int limit) {
		return publicFeed(PageRequest.of(0, Math.clamp(limit, 1, 20))).getContent().stream()
				.map(notice -> new PublicNoticeBrief(notice.getTitle(), notice.getBody(), notice.getPublishAt()))
				.toList();
	}

	/** Published and not yet expired. An absent {@code expiresAt} never expires. */
	private static Criteria withinWindow(Instant now) {
		return new Criteria().andOperator(
				Criteria.where("publishAt").lte(now),
				new Criteria().orOperator(
						Criteria.where("expiresAt").is(null),
						Criteria.where("expiresAt").gt(now)));
	}

	/**
	 * The audience clause for one caller.
	 *
	 * <p>The plain audiences come from the role ({@link NoticeAudience#plainAudiencesFor}). A CLASS
	 * notice needs more: a teacher sees the ones they wrote, and a student the ones for their own
	 * class — either addressed to their section or to the whole class. An admin needs no extra clause,
	 * because CLASS is already in their set, and a staff member never sees one.
	 */
	private Criteria addressedTo(AuthPrincipal caller) {
		List<Criteria> clauses = new ArrayList<>();
		clauses.add(Criteria.where("audience").in(NoticeAudience.plainAudiencesFor(caller.role())));

		switch (caller.role()) {
			case TEACHER -> clauses.add(new Criteria().andOperator(
					Criteria.where("audience").is(NoticeAudience.CLASS),
					Criteria.where("authorUniqueId").is(caller.uniqueId())));
			case STUDENT -> students.findRef(caller.uniqueId())
					.filter(student -> student.classId() != null)
					.ifPresent(student -> clauses.add(forClassOf(student)));
			default -> {
				// ADMIN sees CLASS through the plain set; STAFF is not addressed by one at all.
			}
		}

		return clauses.size() == 1 ? clauses.get(0) : new Criteria().orOperator(clauses);
	}

	private static Criteria forClassOf(StudentRef student) {
		return new Criteria().andOperator(
				Criteria.where("audience").is(NoticeAudience.CLASS),
				Criteria.where("classId").is(student.classId()),
				// A notice with no section is for the whole class, this student included.
				new Criteria().orOperator(
						Criteria.where("section").is(null),
						Criteria.where("section").is(student.section())));
	}

	private Page<Notice> page(Criteria criteria, Pageable pageable) {
		Query query = new Query(criteria);
		long total = mongo.count(query, Notice.class);
		Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), FEED_SORT);
		return new PageImpl<>(mongo.find(query.with(sorted), Notice.class), sorted, total);
	}

	// --- writing ----------------------------------------------------------------------------------

	public Notice create(NoticeRequest request) {
		AuthPrincipal caller = CurrentUser.require();
		Content content = validate(request, caller);
		Instant now = Instant.now(clock);

		Notice saved = notices.insert(Notice.builder()
				.title(content.title())
				.body(content.body())
				.audience(content.audience())
				.classId(content.classId())
				.section(content.section())
				.publiclyVisible(content.publiclyVisible())
				.pinned(content.pinned())
				.attachments(content.attachments())
				.publishAt(content.publishAt())
				.expiresAt(content.expiresAt())
				.authorUniqueId(caller.uniqueId())
				.authorName(caller.name())
				.createdAt(now)
				.updatedAt(now)
				.build());

		audit.record(AuditAction.NOTICE_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	/**
	 * Replaces a notice's content. The author is never reassigned — the board names whoever wrote it,
	 * even when an admin edits it afterwards.
	 */
	public Notice update(String id, NoticeRequest request) {
		AuthPrincipal caller = CurrentUser.require();
		Notice before = require(id);
		requireMayWrite(before, caller);
		Content content = validate(request, caller);

		Notice saved = notices.save(before.toBuilder()
				.title(content.title())
				.body(content.body())
				.audience(content.audience())
				.classId(content.classId())
				.section(content.section())
				.publiclyVisible(content.publiclyVisible())
				.pinned(content.pinned())
				.attachments(content.attachments())
				.publishAt(content.publishAt())
				.expiresAt(content.expiresAt())
				.updatedAt(Instant.now(clock))
				.build());

		audit.record(AuditAction.NOTICE_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	/**
	 * Removes a notice. The attachments stay in storage: a file is cheap, and a notice deleted by
	 * mistake is recoverable from the audit trail only if its attachments are still there.
	 */
	public void delete(String id) {
		AuthPrincipal caller = CurrentUser.require();
		Notice notice = require(id);
		requireMayWrite(notice, caller);
		notices.delete(notice);
		audit.record(AuditAction.NOTICE_DELETED, AUDIT_ENTITY, id, notice, null);
	}

	private Notice require(String id) {
		return notices.findById(id).orElseThrow(() -> NotFoundException.of("Notice", id));
	}

	/** A teacher may only edit or delete their own; an admin may touch any. */
	private void requireMayWrite(Notice notice, AuthPrincipal caller) {
		if (mayWriteAnything(caller)) {
			return;
		}
		if (!caller.uniqueId().equals(notice.getAuthorUniqueId())) {
			throw new ForbiddenException("You may only change notices you wrote");
		}
	}

	private static boolean mayWriteAnything(AuthPrincipal caller) {
		return caller.permissions().contains(Permission.NOTICE_WRITE_ALL);
	}

	// --- validation -------------------------------------------------------------------------------

	/** The normalized, checked content of a write, shared by create and update. */
	private record Content(String title, String body, NoticeAudience audience, String classId, String section,
			boolean publiclyVisible, boolean pinned, List<NoticeAttachment> attachments, Instant publishAt,
			Instant expiresAt) {
	}

	private Content validate(NoticeRequest request, AuthPrincipal caller) {
		boolean writeAnything = mayWriteAnything(caller);
		if (!writeAnything) {
			requireClassNoticeWithoutPrivileges(request);
		}

		String section = normalizeSection(request.section());
		String classId = request.classId() == null || request.classId().isBlank() ? null : request.classId().trim();

		if (request.audience() == NoticeAudience.CLASS) {
			if (classId == null) {
				throw new ValidationException("A class notice has to name a class",
						List.of(new FieldViolation("classId", "is required when audience is CLASS")));
			}
			requireSection(classId, section);
		}
		else if (classId != null || section != null) {
			throw new ValidationException("Only a CLASS notice names a class",
					List.of(new FieldViolation("classId",
							"must be empty unless audience is CLASS, which this one is not")));
		}

		Instant publishAt = request.publishAt() == null ? Instant.now(clock) : request.publishAt();
		if (request.expiresAt() != null && !request.expiresAt().isAfter(publishAt)) {
			throw new ValidationException("The notice would expire before it appears",
					List.of(new FieldViolation("expiresAt", "must be after publishAt")));
		}

		return new Content(request.title().trim(), plainText(request.body()), request.audience(), classId, section,
				request.isPublic(), request.pinned(), validatedAttachments(request.attachments()), publishAt,
				request.expiresAt());
	}

	/** A teacher writes class notices, and cannot put one on the landing page or pin it to the top. */
	private static void requireClassNoticeWithoutPrivileges(NoticeRequest request) {
		if (request.audience() != NoticeAudience.CLASS) {
			throw new ForbiddenException("You may only write notices for a class. Ask the office to post a "
					+ "notice to " + request.audience() + ".");
		}
		if (request.isPublic() || request.pinned()) {
			throw new ForbiddenException("Only an administrator can make a notice public or pin it");
		}
	}

	/** The class has to exist, and to have the section if one was given. */
	private void requireSection(String classId, String section) {
		SchoolClass schoolClass = classes.findById(classId)
				.orElseThrow(() -> new ValidationException("That class does not exist",
						List.of(new FieldViolation("classId", "no class with id " + classId))));
		if (section != null && (schoolClass.getSections() == null
				|| !schoolClass.getSections().contains(section))) {
			throw new ValidationException("That class has no such section",
					List.of(new FieldViolation("section", schoolClass.getName() + " has no section " + section)));
		}
	}

	private List<NoticeAttachment> validatedAttachments(List<NoticeRequest.Attachment> requested) {
		if (requested == null || requested.isEmpty()) {
			return List.of();
		}
		List<NoticeAttachment> result = new ArrayList<>(requested.size());
		for (int i = 0; i < requested.size(); i++) {
			NoticeRequest.Attachment attachment = requested.get(i);
			attachments.requireOwnUrl(attachment.url(), "attachments[" + i + "].url");
			result.add(new NoticeAttachment(attachment.name().trim(), attachment.url(), attachment.size()));
		}
		return result;
	}

	/**
	 * Plain text with its line breaks kept: line endings normalized to {@code \n} so the same notice
	 * reads the same whether it was typed on Windows or a Mac, and the ends trimmed. Nothing is
	 * stripped or escaped — the body is text, and it is the frontend's job never to render it as HTML.
	 */
	private static String plainText(String body) {
		return body.replace("\r\n", "\n").replace('\r', '\n').trim();
	}

	private static String normalizeSection(String section) {
		return section == null || section.isBlank() ? null : section.trim().toUpperCase(Locale.ROOT);
	}
}
