# Backend Tasks

Do the tasks in order unless told otherwise. Each task assumes the previous ones are done. Before starting any task, read `CLAUDE.md` and the relevant section of `API_CONTRACT.md`.

How to use: in the IDE, ask Claude: *"Do task B3 from TASKS.md."*

---

## Phase 0 — Foundation

### B0. Project skeleton
- Set up a Maven project with the stack from `CLAUDE.md` and the full package layout, using empty packages with a `package-info.java` in each.
- Add `docker-compose.yml` with MongoDB 7 as a single-node replica set (include the init script) and MinIO (with bucket auto-create). Add `application.yml` plus `application-local.yml`, where the local profile reads from `.env`, and commit a `.env.example`.
- Add these common building blocks:
    - `PageResponse<T>` in the shape from the contract
    - a global `@RestControllerAdvice` returning `ProblemDetail`, with the error types from the contract
    - a `Clock` bean (inject it everywhere instead of calling `LocalDate.now()`)
    - springdoc configuration
    - Actuator health endpoint
- Add a Spring Modulith `ApplicationModules.verify()` test.
- Add a GitHub Actions workflow that runs `./mvnw verify`.

**Done when:** the app starts locally against docker compose, `/actuator/health` returns UP, and CI passes.

### B1. School config and seeding
- Create a `SchoolConfig` document (one per deployment) holding:
    - identity: name, code, tagline, logo URL, favicon URL, theme colors (primary, secondary, accent)
    - landing-page content: about, vision, principal's message, facilities list, gallery image URLs, stats (students, teachers, years, pass %), contact, map embed URL, social links
    - grading scheme: `MARKS`, `GRADES` or `BOTH`, with grade bands
    - academic settings: working days, attendance edit window, receipt prefix, and the text for receipt and salary-slip footers
- On startup, if the config is missing, load it from `seed/school-seed.json`. Include a realistic sample seed file.
- Add the endpoints `GET /public/school`, `GET /school/config` and `PUT /school/config`. The public endpoint returns only public-safe fields.
- Add an `ImageUploadService` in common/storage for the logo and gallery, storing files in MinIO. Public images go under the `public/` prefix with a public-read policy.

**Done when:** a fresh database is seeded automatically and the public endpoint returns the landing-page content.

### B2. Auth, users, permissions
- The `users` collection holds uniqueId, passwordHash, role, `mustChangePassword`, enabled flag, failed-login counter, `lockedUntil`, `lastLoginAt` and `profileRef` (the student or employee id).
- Create a `Permission` enum and a `RolePermissions` mapping. Suggested permissions include:
    - `STUDENT_READ_FULL`, `STUDENT_READ_BASIC`, `STUDENT_WRITE`
    - `EMPLOYEE_READ`, `EMPLOYEE_WRITE`
    - `ATTENDANCE_MARK_STUDENT`, `ATTENDANCE_MARK_EMPLOYEE`
    - `MARKS_WRITE`, `EXAM_MANAGE`, `EXAM_PAPER_UPLOAD`, `EXAM_PAPER_READ`
    - `FEE_MANAGE`, `FEE_READ_FULL`, `FEE_READ_STATUS`, `FEE_PAY_SELF`
    - `PAYROLL_MANAGE`, `PAYROLL_READ_SELF`
    - `NOTICE_WRITE_ALL`, `NOTICE_WRITE_CLASS`, `NOTICE_READ`
    - `QUIZ_MANAGE`, `QUIZ_ATTEMPT`
    - `SCHOOL_CONFIG_MANAGE`, `DASHBOARD_ADMIN`, `AI_USE`

  Put the permission list in the JWT and in `/auth/me`.
- Implement everything in the contract's auth section:
    - The access token is a JWT (HS256).
    - The refresh token is an opaque random value, stored hashed in the `refresh_tokens` collection with a TTL index, rotated on each use, and sent as an `httpOnly`, `Secure`, `SameSite=Strict` cookie scoped to path `/api/v1/auth`.
    - If a refresh token is reused, revoke the whole token family.
    - Lock the account for 15 minutes after 5 failed logins.
    - A `mustChangePassword` filter blocks every other endpoint while the flag is set.
- On first boot, create the admin from the `BOOTSTRAP_ADMIN_*` env vars. Give this admin an EMP uniqueId and `mustChangePassword=false`.

**Done when:** the full login → refresh → logout flow is covered by tests, reusing a refresh token revokes the family, and the lockout works.

### B3. Unique ID generator and audit log
- Create an `IdGenerator` backed by a `counters` collection, using an atomic `findAndModify` with `$inc` and upsert. Use the format from contract §4.
- Write a concurrency test: 200 parallel generations must produce no duplicates.
- Build the audit log as an `AuditService.record(action, entityType, entityId, before, after)` call. The actor comes from the security context. Add indexes on `(entityType, entityId)` and `timestamp`. Add `GET /audit?entityType=&entityId=` for ADMIN.

**Done when:** the concurrency test passes and audit entries appear.

---

## Phase 1 — Core data

### B4. Academics
- Sessions: exactly one is active at a time, enforced in the service. Classes carry an ordering index and sections. Subjects are assigned to classes.
- `PUT /classes/{id}/assignments` sets the class teacher per section and the teacher for each subject and section.
- Add `AcademicContext.currentSession()`, which other modules use.

### B5. Students
- The `Student` document holds:
    - uniqueId, name, DOB, gender, photo, admission number and date
    - current enrollment (session, class, section, roll number)
    - an enrollment history array
    - guardian details (father, mother, guardian: name, phone, email, occupation)
    - address, blood group, previous school, status
- Creating a student:
    1. Generate the STU id.
    2. Create the user with a random 10-character temporary password and `mustChangePassword=true`.
    3. Return the password in the response **once**.
    4. Do all of this in a transaction.
- Build the three projections (Admin, Teacher, Self) exactly as the contract describes. The Teacher view's `feeStatus` comes from the fees module through a service interface; until B11 exists, use a stub that returns `DUE`.
- List endpoint: filters as in the contract; `q` searches name, uniqueId and guardian phone using a regex prefix and indexes.
- Excel import:
    - Provide a template download.
    - Validate every row first. If any row fails, write nothing and return all the errors. If every row is valid, write them all in batches.
    - Generate a downloadable credentials sheet (.xlsx, containing uniqueId and temporary password) and keep it in MinIO for 24 hours.
- Promotion: a bulk move to the next session and class. The enrollment history is kept, and students in the final class become `ALUMNI`.

**Done when:** a teacher's GET on a student contains no fee amounts (this has a test), and importing 500 rows works.

### B6. Employees
- A single `Employee` document with `employeeType` `TEACHER` or `STAFF`. It holds:
    - common fields: uniqueId, name, DOB, gender, phone, email, address, joining date, photo, status
    - bank details, stored encrypted at field level with an `EncryptionService` using AES-GCM and a key from env
    - PAN, PAN and Aadhaar last 4 digits only
    - teacher-only fields: qualification, subjects, experience
    - staff-only fields: designation, `hasLogin`
- A login is created for teachers always, and for staff only when `hasLogin` is true.
- Add import the same way as in B5.

### B7. Member search
- Implement `GET /members/{uniqueId}` and `GET /members/search?q=` as in the contract. The prefix (`STU`/`EMP`) determines which service handles the lookup, and the result is projected for the caller's role.

**Done when:** a teacher can look up a student but gets 403 on another employee.

---

## Phase 2 — Academics in use

### B8. Attendance
- Students use a bucket document per class, section and date: `{sessionId, classId, sectionId, date, entries:[{studentId, uniqueId, status}], markedBy, markedAt}`. Put a unique index on `(classId, sectionId, date)` and a multikey index on `entries.uniqueId`.
- Enforce the edit window from school config for teachers. Admins can edit any date. Every change is audited.
- The per-student range query returns daily records plus a summary: counts per status and a percentage, where HOLIDAY and LEAVE are excluded from the denominator.
- Employee attendance uses the same bucket pattern with one document per date. Only admins can mark it.
- Add a holiday calendar in academics. Marking a date as a holiday makes it read as HOLIDAY for everyone.

### B9. Exams, marks, report cards
- An exam has a session, name, term, classes, a subject schedule `[{subjectId, date, maxMarks, passMarks}]`, and a status of `DRAFT`, `ONGOING`, `MARKS_ENTRY` or `PUBLISHED`.
- Store marks as one document per exam, student and subject, holding marks obtained, absent flag, remarks and `enteredBy`. Bulk upsert per class, section and subject. Reject any value over `maxMarks`. Once an exam is published, marks are locked.
- Results: per-subject marks, total, percentage, grade (from the config bands), rank within the section, and pass or fail.
- Report card PDF: built with OpenPDF using the school logo, name and colors from config, with a signature line and the footer from config.

### B10. Notices
- A notice has title, body (sanitized HTML), audience, public flag, pinned flag, attachments (in MinIO), `publishAt`, `expiresAt` and author.
- Listing filters by the caller's audience. A teacher can only create notices for classes they teach.

---

## Phase 3 — Money

### B11. Fees
- Fee heads and fee structures as in the contract. An installment is `{name, dueDate, items:[{headId, amount}]}`, and a late fine rule is `{type: FLAT|PER_DAY, amount, graceDays, cap}`.
- `generate-invoices` creates one invoice per student per installment. It is idempotent because of a unique key on `(studentId, structureId, installmentName)`, and it applies any concessions.
- An invoice has status `UNPAID`, `PARTIAL`, `PAID` or `CANCELLED`. The late fine is computed when the invoice is read, and is only frozen into the invoice when a payment is made.
- Totals come from an aggregation. `feeStatus` rules: `PAID` means no balance; `OVERDUE` means some unpaid invoice is past its due date plus grace days; `PARTIAL` means something has been paid but a balance remains; otherwise `DUE`. This replaces the B5 stub.
- Offline payments are recorded with mode `CASH`, `CHEQUE`, `UPI_OFFLINE` or `BANK_TRANSFER`, and generate a receipt the same way online payments do (B12).
- Reports: collection by date range, grouped by mode and by head; a defaulters list.

### B12. Online payments (Razorpay)
- Put the integration behind a `PaymentGateway` interface with a `RazorpayGateway` implementation.
- Order flow:
    1. Validate that the invoices belong to the caller and are unpaid.
    2. Compute the amount on the server, including the late fine.
    3. Create a Razorpay order.
    4. Save a `payments` document with status `CREATED`, `payerName` and `payerRelation`.
- `/payments/verify` checks the signature and, if it is valid, marks the payment `AUTHORIZED_PENDING_CONFIRMATION`.
- `/public/payments/webhook` verifies the webhook signature, then handles `payment.captured` and `payment.failed`. It must be idempotent, using a unique index on `gatewayPaymentId` and on the event id. Inside one transaction it:
    1. marks the payment `CAPTURED`
    2. applies the amount to the invoices
    3. assigns the next receipt number from the counter (`{receiptPrefix}/{session}/{seq}`)
    4. publishes `PaymentCapturedEvent`
- Receipt PDF: school header, receipt number, student, class, invoices and heads paid, amount in figures and words (Indian format), mode, gateway reference, "Paid by: {payerName} ({relation})", and date. Generate it once and store it in MinIO; later downloads use the stored copy.
- Add a scheduled reconciliation job (every 30 minutes) that fetches Razorpay's status for payments stuck in `CREATED` or `AUTHORIZED_PENDING_CONFIRMATION` for more than 15 minutes, and settles them.
- Write tests using a fake gateway, including a duplicate-webhook test and a tampered-signature test.

### B13. Payroll
- A salary structure per employee holds basic, allowances `[{name, amount}]` and deductions `[{name, amount|percent}]`, with an effective-from date (keep a history).
- A payroll run for a month creates one PENDING record per active employee. It can prorate by that employee's attendance (a toggle in config), deducts advance installments, and is idempotent.
- Paying a record requires mode, reference and date, and is audited.
- `/payroll/mine` returns records plus totals for paid and unpaid.
- Salary slip PDF.

---

## Phase 4 — Engagement

### B14. Exam paper vault
- Upload a PDF to MinIO under a private prefix. Validate the file: PDF only, at most 20 MB, checked by magic bytes and not just the extension. Store metadata including `releaseAt`.
- Download follows the rule in the contract, returning 423 before release for teachers. It returns a pre-signed URL valid for 5 minutes, and every download is audited.
- Only admins and the teacher mapped to that subject and class can upload. Uploading again creates a new version and keeps the old one.

### B15. Quizzes
- A quiz has title, class, section(s), subject, time limit, start and end window, max attempts, shuffle flag, and questions. Question types are `MCQ_SINGLE`, `MCQ_MULTI` and `TRUE_FALSE`, each with options, correct answers, marks and explanation.
- Starting an attempt returns the questions without the answers (and shuffled if enabled). Submitting grades automatically, enforces the time limit on the server, and shows the explanations after submission if the quiz allows it.
- Results: per-student scores and per-question accuracy.

### B16. Dashboards
- Build each dashboard as one aggregation-backed endpoint per role with the contents described in the contract. Cache the admin dashboard for 60 seconds using Caffeine.

### B17. Notifications (optional)
- Create a `NotificationService` interface with email (SMTP) as the first implementation. It sends fee-due reminders (a scheduled job 3 days before the due date and on the due date) and absence alerts to the guardian email. Leave room for SMS or WhatsApp providers later. It does nothing unless configured.

---

## Phase 5 — AI and hardening

### B18. Spring AI
- Everything is gated by `app.features.ai`. Use a `ChatClient` whose provider is chosen by config.
- quiz-draft: use structured output mapped to the quiz question DTO. The result is never saved automatically; a teacher reviews it first.
- report-remarks: the prompt includes marks, trend and attendance only, and **no personal identifiers beyond the first name**.
- insights: compute the at-risk signals in code first (attendance below 75%, marks falling across exams, fees overdue), then have the model write an explanation for each flagged student.
- Public chat: retrieval-augmented generation over the school config text and public notices. Rate-limit to 10 requests per minute per IP using Bucket4j. The system prompt keeps the bot to school topics.

### B19. Hardening and replication kit
- Security: rate limiting on `/auth/login`; security headers; limits on request size; confirm with a test that every non-public endpoint requires auth.
- Build a multi-stage `Dockerfile` (JRE 21, non-root user).
- Write `DEPLOY.md`, a runbook for bringing up a new school:
    1. env vars
    2. seed file
    3. docker compose for production (backend, MongoDB with a volume, MinIO or external S3, Nginx serving the frontend and proxying `/api`, TLS via Certbot)
    4. backups: nightly `mongodump` to object storage
    5. Razorpay webhook setup
- Write `scripts/new-school.sh`, which copies the templates and prompts for the school code and name.