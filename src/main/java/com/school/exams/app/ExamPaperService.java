package com.school.exams.app;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.school.academics.app.SchoolClassService;
import com.school.academics.app.SubjectService;
import com.school.academics.domain.Subject;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditLog;
import com.school.common.audit.AuditSearch;
import com.school.common.audit.AuditService;
import com.school.common.config.AppProperties;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.ErrorType;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.common.storage.ObjectStorage;
import com.school.exams.api.ExamPaperResponse;
import com.school.exams.api.PaperDownloadResponse;
import com.school.exams.domain.Exam;
import com.school.exams.domain.ExamPaper;
import com.school.exams.domain.PaperVersion;
import com.school.exams.infra.ExamPaperRepository;
import com.school.exams.infra.ExamRepository;
import com.school.people.app.EmployeeRef;
import com.school.people.app.EmployeeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * The exam paper vault: uploading question papers, versioning them, and letting them out at a time.
 *
 * <p>Three rules make it a vault rather than a folder.
 *
 * <ol>
 * <li><strong>The bytes are private.</strong> Papers go under {@code papers/}, outside the publicly
 * readable prefix, under a random key that is never returned by any endpoint. A download is a
 * pre-signed URL valid for five minutes, issued only after the check below.
 * <li><strong>Release is enforced per caller, not per record.</strong> An admin and the teacher who
 * uploaded it may read it at any time — somebody has to be able to proofread a paper before the
 * exam. Every other teacher is refused with 423 until {@code releaseAt}, and told when it opens.
 * Students and staff never reach here: they do not hold {@code EXAM_PAPER_READ}, so the endpoint
 * itself answers 403.
 * <li><strong>Nothing is overwritten and nothing is unlogged.</strong> A corrected paper is a new
 * version beside the old one, and every upload, version, release change and download is an entry in
 * {@code audit_logs} naming who, which paper and which version.
 * </ol>
 *
 * <p>"Is this caller an admin" is asked as {@code EXAM_MANAGE} rather than by role, per CLAUDE.md
 * rule 1: whoever runs the exams owns the papers for them.
 */
@Service
public class ExamPaperService {

	static final String AUDIT_ENTITY = "ExamPaper";

	/** Key prefix. Deliberately not under {@code app.storage.public-prefix}. */
	private static final String PREFIX = "papers/";

	private static final String PDF_CONTENT_TYPE = "application/pdf";

	/** A PDF begins with these four bytes. The extension and the Content-Type are not evidence. */
	private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

	/** How long a download link lives. Long enough to click, short enough not to be worth passing on. */
	private static final Duration URL_TTL = Duration.ofMinutes(5);

	/** "14 Oct 2026, 10:00", in the school's timezone, for the 423 detail. */
	private static final DateTimeFormatter RELEASE_FORMAT =
			DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

	/** S3 user metadata key the uploader's file name is kept under. */
	private static final String NAME_METADATA = "filename";

	private static final int MAX_TITLE_LENGTH = 160;

	private static final int MAX_FILE_NAME_LENGTH = 120;

	private final ExamPaperRepository papers;
	private final ExamRepository exams;
	private final SchoolClassService classes;
	private final SubjectService subjects;
	private final EmployeeService employees;
	private final ObjectStorage storage;
	private final AuditService audit;
	private final AppProperties properties;
	private final Clock clock;

	public ExamPaperService(ExamPaperRepository papers, ExamRepository exams, SchoolClassService classes,
			SubjectService subjects, EmployeeService employees, ObjectStorage storage, AuditService audit,
			AppProperties properties, Clock clock) {
		this.papers = papers;
		this.exams = exams;
		this.classes = classes;
		this.subjects = subjects;
		this.employees = employees;
		this.storage = storage;
		this.audit = audit;
		this.properties = properties;
		this.clock = clock;
	}

	// --- reading ----------------------------------------------------------------------------------

	/** The vault, optionally narrowed to one exam and one class. Metadata only. */
	public List<ExamPaperResponse> list(String examId, String classId) {
		boolean byExam = examId != null && !examId.isBlank();
		boolean byClass = classId != null && !classId.isBlank();
		List<ExamPaper> found;
		if (byExam && byClass) {
			found = papers.findByExamIdAndClassIdOrderByTitleAsc(examId, classId);
		}
		else if (byExam) {
			found = papers.findByExamIdOrderByTitleAsc(examId);
		}
		else if (byClass) {
			found = papers.findByClassIdOrderByTitleAsc(classId);
		}
		else {
			found = papers.findAllByOrderByTitleAsc();
		}
		return responses(found);
	}

	/**
	 * Issues a download link for one version, defaulting to the latest.
	 *
	 * @throws AppException 423 {@code LOCKED} when a teacher who did not upload it asks before
	 *                      {@code releaseAt}, with the release time in the detail
	 */
	public PaperDownloadResponse download(String id, Integer versionNo) {
		ExamPaper paper = require(id);
		AuthPrincipal caller = CurrentUser.require();
		if (!mayReadWhileLocked(paper, caller) && isLocked(paper)) {
			throw locked(paper);
		}
		PaperVersion version = versionNo == null ? paper.latestVersion() : versionOf(paper, versionNo);

		String url = storage.presignedUrl(version.storageKey(), URL_TTL, PDF_CONTENT_TYPE, version.fileName());
		// Written before the caller has the URL, and never with the URL in it: the entry records that
		// access was granted, which is the thing an exam leak investigation needs.
		audit.record(AuditAction.EXAM_PAPER_DOWNLOADED, AUDIT_ENTITY, paper.getId(),
				"version " + version.versionNo() + " of " + paper.getTitle());
		return new PaperDownloadResponse(url, URL_TTL.toSeconds());
	}

	/** Everything the trail holds about one paper: uploads, versions, release changes and downloads. */
	public Page<AuditLog> accessLog(String id, Pageable pageable) {
		require(id);
		return audit.search(new AuditSearch(AUDIT_ENTITY, id, null, null, null), pageable);
	}

	// --- writing ----------------------------------------------------------------------------------

	/**
	 * Stores a paper. The first upload for a (exam, class, subject) triple creates it; a later one adds
	 * a version and keeps the old ones.
	 *
	 * @param releaseAt required on the first upload and must be in the future. On a further version it
	 *                  is optional: left out, the paper keeps the release time it has
	 * @param title     optional; defaults to "{exam} — {subject}"
	 */
	public ExamPaperResponse upload(MultipartFile file, String examId, String classId, String subjectId,
			Instant releaseAt, String title) {
		Exam exam = exams.findById(examId).orElseThrow(() -> NotFoundException.of("Exam", examId));
		requireClassSitsExam(exam, classId);
		requireSubjectInSchedule(exam, subjectId);

		byte[] bytes = readPdf(file);
		String fileName = safeFileName(file.getOriginalFilename());
		AuthPrincipal caller = CurrentUser.require();
		Instant now = Instant.now(clock);

		Optional<ExamPaper> existing = papers.findByExamIdAndClassIdAndSubjectId(examId, classId, subjectId);
		if (existing.isPresent()) {
			return addVersion(existing.get(), bytes, fileName, releaseAt, caller, now);
		}
		return create(exam, classId, subjectId, title, requireFutureRelease(releaseAt, now), bytes, fileName,
				caller, now);
	}

	/**
	 * Moves the release time.
	 *
	 * <p>An admin may do it whenever. The uploader may only do it while the paper is still locked —
	 * once a paper is out, pulling it back would not unsee it, and the office should know that the
	 * schedule moved.
	 */
	public ExamPaperResponse changeReleaseAt(String id, Instant releaseAt) {
		ExamPaper before = require(id);
		AuthPrincipal caller = CurrentUser.require();
		if (!isAdmin(caller)) {
			if (!isCreator(before, caller)) {
				throw new ForbiddenException("Only the teacher who uploaded this paper, or the office, may "
						+ "change its release time");
			}
			if (!isLocked(before)) {
				throw new BusinessRuleException("This paper has already been released; ask the office to change "
						+ "its release time.");
			}
		}
		ExamPaper saved = papers.save(before.toBuilder()
				.releaseAt(releaseAt)
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.EXAM_PAPER_RELEASE_CHANGED, AUDIT_ENTITY, id,
				Map.of("releaseAt", before.getReleaseAt()), Map.of("releaseAt", releaseAt));
		return response(saved);
	}

	private ExamPaperResponse create(Exam exam, String classId, String subjectId, String title, Instant releaseAt,
			byte[] bytes, String fileName, AuthPrincipal caller, Instant now) {
		String key = PREFIX + UUID.randomUUID() + ".pdf";
		storage.put(key, bytes, PDF_CONTENT_TYPE, Map.of(NAME_METADATA, fileName));

		PaperVersion version = new PaperVersion(1, key, fileName, bytes.length, caller.uniqueId(), now);
		ExamPaper paper = ExamPaper.builder()
				.examId(exam.getId())
				.classId(classId)
				.subjectId(subjectId)
				.title(resolvedTitle(title, exam, subjectId))
				.releaseAt(releaseAt)
				.versions(List.of(version))
				.createdBy(caller.uniqueId())
				.createdAt(now)
				.updatedAt(now)
				.build();

		ExamPaper saved;
		try {
			saved = papers.insert(paper);
		}
		catch (DuplicateKeyException ex) {
			// Two first uploads for the same triple raced. The loser's bytes are nobody's, so they go.
			storage.delete(key);
			throw new ConflictException("A paper for that exam, class and subject was just uploaded. Upload "
					+ "again to add your file as a new version of it.", ex);
		}
		audit.record(AuditAction.EXAM_PAPER_UPLOADED, AUDIT_ENTITY, saved.getId(), null, auditView(saved, version));
		return response(saved);
	}

	private ExamPaperResponse addVersion(ExamPaper before, byte[] bytes, String fileName, Instant releaseAt,
			AuthPrincipal caller, Instant now) {
		if (!isAdmin(caller) && !isCreator(before, caller)) {
			throw new ForbiddenException("A paper for that exam, class and subject already exists. Only the "
					+ "teacher who uploaded it, or the office, may add a new version.");
		}
		String key = PREFIX + UUID.randomUUID() + ".pdf";
		storage.put(key, bytes, PDF_CONTENT_TYPE, Map.of(NAME_METADATA, fileName));

		PaperVersion version = new PaperVersion(before.latestVersion().versionNo() + 1, key, fileName,
				bytes.length, caller.uniqueId(), now);
		List<PaperVersion> versions = new ArrayList<>(before.getVersions());
		versions.add(version);

		ExamPaper saved = papers.save(before.toBuilder()
				.versions(List.copyOf(versions))
				.releaseAt(releaseAt == null ? before.getReleaseAt() : releaseAt)
				.updatedAt(now)
				.build());
		audit.record(AuditAction.EXAM_PAPER_VERSION_ADDED, AUDIT_ENTITY, saved.getId(),
				auditView(before, before.latestVersion()), auditView(saved, version));
		return response(saved);
	}

	// --- authorisation ----------------------------------------------------------------------------

	/** Whoever runs the exams. Asked as a permission, never as a role name. */
	private static boolean isAdmin(AuthPrincipal caller) {
		return caller.permissions().contains(Permission.EXAM_MANAGE);
	}

	private static boolean isCreator(ExamPaper paper, AuthPrincipal caller) {
		return caller.uniqueId() != null && caller.uniqueId().equals(paper.getCreatedBy());
	}

	private static boolean mayReadWhileLocked(ExamPaper paper, AuthPrincipal caller) {
		return isAdmin(caller) || isCreator(paper, caller);
	}

	private boolean isLocked(ExamPaper paper) {
		return paper.getReleaseAt().isAfter(Instant.now(clock));
	}

	private AppException locked(ExamPaper paper) {
		Instant releaseAt = paper.getReleaseAt();
		long retryAfter = Math.max(1, Duration.between(Instant.now(clock), releaseAt).toSeconds());
		return new AppException(ErrorType.LOCKED,
				"Available from " + RELEASE_FORMAT.format(releaseAt.atZone(clock.getZone())),
				Map.of("releaseAt", releaseAt.toString(), "retryAfterSeconds", retryAfter), null);
	}

	// --- validation -------------------------------------------------------------------------------

	private ExamPaper require(String id) {
		return papers.findById(id).orElseThrow(() -> NotFoundException.of("Exam paper", id));
	}

	private static PaperVersion versionOf(ExamPaper paper, int versionNo) {
		return paper.getVersions().stream()
				.filter(version -> version.versionNo() == versionNo)
				.findFirst()
				.orElseThrow(() -> new NotFoundException("This paper has no version " + versionNo));
	}

	private void requireClassSitsExam(Exam exam, String classId) {
		if (classes.findById(classId).isEmpty()) {
			throw new ValidationException("That class does not exist",
					List.of(new FieldViolation("classId", "no class with id " + classId)));
		}
		if (exam.getClassIds() == null || !exam.getClassIds().contains(classId)) {
			throw new ValidationException("That class does not sit this exam",
					List.of(new FieldViolation("classId", exam.getName() + " is not set for that class")));
		}
	}

	private static void requireSubjectInSchedule(Exam exam, String subjectId) {
		if (ExamService.paperFor(exam, subjectId).isEmpty()) {
			throw new ValidationException("That subject is not a paper in this exam",
					List.of(new FieldViolation("subjectId", "is not in the schedule of " + exam.getName())));
		}
	}

	private static Instant requireFutureRelease(Instant releaseAt, Instant now) {
		if (releaseAt == null) {
			throw new ValidationException("The release time is missing",
					List.of(new FieldViolation("releaseAt", "must be given when a paper is first uploaded")));
		}
		if (!releaseAt.isAfter(now)) {
			throw new ValidationException("The release time has already passed",
					List.of(new FieldViolation("releaseAt", "must be in the future")));
		}
		return releaseAt;
	}

	/**
	 * Reads the upload and checks it is a PDF by its own leading bytes, not by what the client called
	 * it. Without this, "papers are PDFs" would be a convention rather than a fact, and the vault would
	 * hand out whatever was put in it.
	 */
	private byte[] readPdf(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ValidationException("The file is missing or empty",
					List.of(new FieldViolation("file", "must not be empty")));
		}
		if (file.getSize() > properties.storage().maxPaperSize().toBytes()) {
			throw new ValidationException("The file is too large", List.of(new FieldViolation("file",
					"must not be larger than " + properties.storage().maxPaperSize().toMegabytes() + " MB")));
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException ex) {
			throw new ValidationException("The file could not be read",
					List.of(new FieldViolation("file", "could not be read")));
		}
		if (!startsWithPdfMagic(bytes)) {
			throw new ValidationException("That file is not a PDF",
					List.of(new FieldViolation("file", "must be a PDF file (its first bytes must be %PDF)")));
		}
		return bytes;
	}

	private static boolean startsWithPdfMagic(byte[] bytes) {
		if (bytes.length < PDF_MAGIC.length) {
			return false;
		}
		for (int i = 0; i < PDF_MAGIC.length; i++) {
			if (bytes[i] != PDF_MAGIC[i]) {
				return false;
			}
		}
		return true;
	}

	/** No directory parts, ASCII only, never empty: it is echoed into a Content-Disposition header. */
	private static String safeFileName(String originalName) {
		String fallback = "paper.pdf";
		if (originalName == null || originalName.isBlank()) {
			return fallback;
		}
		String base = originalName.trim();
		int lastSeparator = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
		if (lastSeparator >= 0) {
			base = base.substring(lastSeparator + 1);
		}
		base = base.replaceAll("[^A-Za-z0-9 ._()-]", "_").trim();
		if (base.length() > MAX_FILE_NAME_LENGTH) {
			base = base.substring(0, MAX_FILE_NAME_LENGTH);
		}
		if (base.isBlank() || base.equals(".") || base.equals("..")) {
			return fallback;
		}
		return base.toLowerCase(Locale.ROOT).endsWith(".pdf") ? base : base + ".pdf";
	}

	/** The title given, or "{exam} — {subject}", which is what a vault listing needs to read as. */
	private String resolvedTitle(String title, Exam exam, String subjectId) {
		if (title != null && !title.isBlank()) {
			String trimmed = title.trim();
			return trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) : trimmed;
		}
		return exam.getName() + " — " + subjects.get(subjectId).getName();
	}

	// --- mapping ----------------------------------------------------------------------------------

	private ExamPaperResponse response(ExamPaper paper) {
		return responses(List.of(paper)).get(0);
	}

	/**
	 * Resolves the exam, class, subject and people names once for the whole page rather than per row,
	 * so a listing is a handful of reads whatever its size. A name that cannot be resolved falls back
	 * to the id it was looked up by, so a deleted subject leaves a readable row instead of failing the
	 * request.
	 */
	private List<ExamPaperResponse> responses(List<ExamPaper> found) {
		if (found.isEmpty()) {
			return List.of();
		}
		Map<String, String> examNames = exams.findAllById(distinct(found, ExamPaper::getExamId)).stream()
				.collect(Collectors.toMap(Exam::getId, Exam::getName));
		Map<String, String> subjectNames = subjects.list().stream()
				.collect(Collectors.toMap(Subject::getId, Subject::getName));
		Map<String, String> classNames = new LinkedHashMap<>();
		distinct(found, ExamPaper::getClassId).forEach(id ->
				classes.findById(id).ifPresent(schoolClass -> classNames.put(id, schoolClass.getName())));

		Map<String, String> peopleNames = new LinkedHashMap<>();
		found.forEach(paper -> {
			peopleNames.computeIfAbsent(paper.getCreatedBy(), this::nameOf);
			paper.getVersions().forEach(version ->
					peopleNames.computeIfAbsent(version.uploadedBy(), this::nameOf));
		});

		Instant now = Instant.now(clock);
		List<ExamPaperResponse> result = new ArrayList<>(found.size());
		for (ExamPaper paper : found) {
			PaperVersion latest = paper.latestVersion();
			boolean lockedNow = paper.getReleaseAt().isAfter(now);
			result.add(new ExamPaperResponse(
					paper.getId(),
					paper.getTitle(),
					paper.getExamId(),
					examNames.getOrDefault(paper.getExamId(), paper.getExamId()),
					paper.getClassId(),
					classNames.getOrDefault(paper.getClassId(), paper.getClassId()),
					paper.getSubjectId(),
					subjectNames.getOrDefault(paper.getSubjectId(), paper.getSubjectId()),
					paper.getReleaseAt(),
					lockedNow,
					new ExamPaperResponse.Version(latest.versionNo(), latest.fileName(), latest.sizeBytes(),
							latest.uploadedBy(), peopleNames.get(latest.uploadedBy()), latest.uploadedAt()),
					paper.getVersions().size(),
					paper.getCreatedBy(),
					peopleNames.get(paper.getCreatedBy()),
					paper.getCreatedAt()));
		}
		return result;
	}

	/** The employee's display name, or the uniqueId itself — the bootstrap admin has no record. */
	private String nameOf(String uniqueId) {
		return employees.findRef(uniqueId).map(EmployeeRef::name).orElse(uniqueId);
	}

	private static List<String> distinct(List<ExamPaper> found, Function<ExamPaper, String> field) {
		return found.stream().map(field).distinct().toList();
	}

	/** What an audit entry holds: the paper and the version in question, never the storage key. */
	private static Map<String, Object> auditView(ExamPaper paper, PaperVersion version) {
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("title", paper.getTitle());
		view.put("examId", paper.getExamId());
		view.put("classId", paper.getClassId());
		view.put("subjectId", paper.getSubjectId());
		view.put("releaseAt", paper.getReleaseAt());
		view.put("versionNo", version.versionNo());
		view.put("fileName", version.fileName());
		view.put("sizeBytes", version.sizeBytes());
		view.put("uploadedBy", version.uploadedBy());
		return view;
	}
}
