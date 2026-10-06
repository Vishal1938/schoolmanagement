# API Contract — School Management System

This file is shared by the backend (IntelliJ) and frontend (VS Code) repos. **Keep an identical copy in both repos.** If a task requires changing the contract, change it here first, then copy it to the other repo.

Once the backend is running, the live source of truth is the OpenAPI spec at `/v3/api-docs`. The frontend generates its TypeScript types from it.

---

## 1. General conventions

| Topic | Rule |
|---|---|
| Base path | `/api/v1` |
| Public endpoints | `/api/v1/public/**` (no auth) |
| Deployment | Frontend and backend on the same origin; Nginx proxies `/api` to Spring Boot, so no CORS is needed in production |
| Format | JSON, camelCase field names |
| Dates | `LocalDate` → `"2026-09-26"`; timestamps → ISO-8601 UTC `"2026-09-26T10:15:30Z"` |
| Money | **Integer paise** (`long`), never floating point. ₹1,250.50 → `125050`. The UI formats it. |
| IDs | `id` = Mongo ObjectId (internal). `uniqueId` = human ID (e.g. `SVM-STU-26-00142`), used in URLs where a person is addressed |
| Errors | RFC 7807 `ProblemDetail` with `code` and `timestamp` (see §3); branch on `code` |
| Validation errors | HTTP 400, `code = VALIDATION_ERROR`, with `errors: [{field, message}]` |

### Pagination

Request: `?page=0&size=20&sort=name,asc` (page is 0-based; size is at most 100)

A `size` above 100 is **capped, not rejected** — the response comes back with `size: 100`, so check
`size` rather than assuming the request was honoured. Omitting `size` gives 20.

Response:
```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 134,
  "totalPages": 7,
  "first": true,
  "last": false,
  "empty": false
}
```

`first`, `last` and `empty` are conveniences derived from the others. Spring's own `content` and
`totalElements` names are never used.

## 2. Auth

| Method | Path | Auth | Body / notes |
|---|---|---|---|
| POST | `/auth/login` | none | `{uniqueId, password}` → `{accessToken, expiresIn, user}`; sets the refresh cookie |
| POST | `/auth/refresh` | cookie | → `{accessToken, expiresIn}`; rotates the refresh token |
| POST | `/auth/logout` | cookie | Deletes the refresh token and clears the cookie → 204, no body |
| POST | `/auth/change-password` | bearer | `{currentPassword, newPassword}` → `{accessToken, expiresIn, user}`, same shape as login |
| GET | `/auth/me` | bearer | Current `user` |

`user` object:
```json
{
  "uniqueId": "SVM-STU-26-00142",
  "name": "Aarav Sharma",
  "role": "STUDENT",
  "permissions": ["NOTICE_READ", "MARKS_READ_SELF"],
  "mustChangePassword": true,
  "profilePhotoUrl": null
}
```

- People log in with their **`uniqueId`**, not an email address. It is matched case-insensitively.
- There is no self-signup. Admins create all accounts, and the initial password is issued by the admin.

### Access token

Send it as `Authorization: Bearer <token>`. It is a JWS (HS256) and expires after **15 minutes**;
`expiresIn` is that lifetime in seconds. Its claims carry the role and permission list, so the server
authorises a request without a database read — which also means a change to the account does not affect
a token already issued. It cannot be revoked; it is short-lived instead. Do not parse it in the
frontend: read `user` from the login response or `/auth/me`.

### Refresh token and the cookie

- An opaque random value, **not** a JWT, stored only as a hash server-side. It lasts **7 days**.
- Sent as a cookie named `refreshToken`, with `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth` and
  `Secure` (off only in local development, which is plain http). Because the path is scoped, the cookie
  is not attached to any request outside `/auth`, and `SameSite=Strict` is what makes a CSRF token
  unnecessary here.
- Nothing in the frontend can or should read it: send `credentials: "include"` and let the browser do it.
- **Every refresh rotates it.** The cookie you sent stops working the moment the call succeeds, and the
  response carries the replacement.
- **Reuse revokes the session.** Presenting an already-rotated token — which only happens if it leaked,
  since the legitimate client discards it on exchange — deletes every token descended from that login and
  answers 401 `TOKEN_INVALID`. Both the attacker and the real user must log in again.
- `logout` deletes the whole family too, so one call ends the session on that device for good.
- Changing a password ends **every** session the account has, not just the current one.

### Forced password change

While `mustChangePassword` is true, only `/auth/me`, `/auth/change-password` and `/auth/logout` are
allowed; every other endpoint returns 403 with `code = PASSWORD_CHANGE_REQUIRED`. `/auth/refresh` keeps
working — it authenticates by cookie and carries no access token — but the token it hands back still has
the flag set, so it unlocks nothing.

`change-password` requires the current password, rejects a `newPassword` shorter than 8 characters
(400 `VALIDATION_ERROR`) or equal to the current one (422 `UNPROCESSABLE`), and returns a **new access
token and refresh cookie**. Use that pair from then on: the token you called it with still says a change
is pending.

### Lockout

Five consecutive wrong passwords lock the account for **15 minutes**. While it is locked every login
attempt answers 423 `LOCKED` — including one with the correct password — and the body carries
`retryAfterSeconds`. A successful login resets the counter. The lock lifts by itself; no admin action is
needed. An unknown `uniqueId` is never locked, and answers exactly like a wrong password.

### Roles

`ADMIN`, `TEACHER`, `STUDENT`, `STAFF`

The frontend must decide what to show using **`permissions`**, not `role`. Each role maps to a fixed set
of permissions, listed in §2.1.

### 2.1 Permissions

The closed set, defined in `backend/.../common/security/Permission.java`. The authority string is the
name exactly as written here. ADMIN holds all of them; the other three hold what is marked.

| Permission | TEACHER | STUDENT | STAFF | Covers |
|---|:-:|:-:|:-:|---|
| `STUDENT_READ_FULL` | | | | A student record including fees, guardians and contact details |
| `STUDENT_READ_BASIC` | ✓ | | | The teacher-safe student projection: no fee amounts, no payment history |
| `STUDENT_WRITE` | | | | Create, update and promote students |
| `EMPLOYEE_READ` | | | | Read employee records |
| `EMPLOYEE_WRITE` | | | | Create and update employees |
| `ATTENDANCE_MARK_STUDENT` | ✓ | | | Mark student attendance, within the configured edit window |
| `ATTENDANCE_MARK_EMPLOYEE` | | | | Mark employee attendance |
| `ATTENDANCE_CORRECT_ANY` | | | | Correct a register after the edit window has closed |
| `HOLIDAY_MANAGE` | | | | Declare and remove holidays. Reading them needs no permission |
| `MARKS_WRITE` | ✓ | | | Enter and update marks until the exam is published |
| `MARKS_READ_SELF` | | ✓ | | Read one's own marks and report cards |
| `EXAM_MANAGE` | | | | Create exams and move them through their statuses |
| `EXAM_PAPER_UPLOAD` | ✓ | | | Upload to the exam-paper vault |
| `EXAM_PAPER_READ` | ✓ | | | Download from the vault; 423 before `releaseAt` for teachers |
| `FEE_MANAGE` | | | | Fee heads, structures, invoices, concessions, offline payments |
| `FEE_READ_FULL` | | | | Invoices and amounts for any student |
| `FEE_READ_STATUS` | ✓ | ✓ | | The derived status only (`PAID`/`DUE`/`PARTIAL`/`OVERDUE`), no amounts |
| `FEE_PAY_SELF` | | ✓ | | Pay one's own invoices online |
| `PAYROLL_MANAGE` | | | | Salary structures, payroll runs, payments |
| `PAYROLL_READ_SELF` | ✓ | | ✓ | Read one's own payroll records and salary slips |
| `NOTICE_WRITE_ALL` | | | | Write a notice for any audience, and make one public or pinned |
| `NOTICE_WRITE_CLASS` | ✓ | | | Write `CLASS` notices, and edit or delete one's own |
| `NOTICE_READ` | ✓ | ✓ | ✓ | Read notices for one's own audience, and download their attachments |
| `QUIZ_MANAGE` | ✓ | | | Create quizzes and read results |
| `QUIZ_ATTEMPT` | | ✓ | | Attempt a quiz |
| `ACADEMICS_MANAGE` | | | | Create and edit sessions, classes, subjects and teaching assignments |
| `SCHOOL_CONFIG_MANAGE` | | | | Read and replace the school configuration |
| `DASHBOARD_ADMIN` | | | | The admin dashboard aggregates |
| `AUDIT_READ` | | | | Read the audit trail |
| `AI_USE` | ✓ | | | The AI endpoints, additionally gated by `app.features.ai` |

A permission is coarse — "may enter marks at all". Object-level rules, such as a teacher only reaching
their own classes or a student only their own records, are enforced per service and surface as 403
`FORBIDDEN`. Permissions are additive across releases and are never renamed.

## 3. Error shape

Every error response is RFC 7807 `application/problem+json`, produced by the single
`@RestControllerAdvice`. Failures raised inside the security filter chain (401, 403) use the same
writer, so they are the same shape.

```json
{
  "type": "https://schoolmanagement.dev/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/students",
  "code": "VALIDATION_ERROR",
  "timestamp": "2026-09-26T10:15:30.123Z",
  "errors": [{ "field": "dob", "message": "must be in the past" }]
}
```

| Field | Always present | Notes |
|---|---|---|
| `type` | yes | `https://schoolmanagement.dev/problems/{code}`, with `code` lowercased and `_` → `-`. Stable per deployment; not the request host. |
| `title` | yes | Short, fixed English label for the code. Not for display — it is not localised. |
| `status` | yes | HTTP status, same as the response status line. |
| `detail` | yes | Human-readable, may name the offending resource. Never contains secrets, tokens or payloads. |
| `instance` | yes | The request path. |
| `code` | yes | **UPPER_SNAKE_CASE machine-readable code — branch on this.** Closed set, listed below. |
| `timestamp` | yes | Server time the problem was created, ISO-8601 UTC. |
| `errors` | validation only | `[{field, message}]`. `field` is a dotted path, e.g. `address.pincode`, or `row.3.dob` for imports. |
| `errorId` | `INTERNAL_ERROR` only | UUID also written to the server log; quote it in bug reports. |

**Switch on `code`, not on `type` or `title`.** `type` is derived from `code` and exists only to
satisfy RFC 7807. Individual errors may carry extra members beyond the table (for example
`retryAfterSeconds` on `LOCKED` and `RATE_LIMITED`); treat unknown members as optional.

### Codes

| `code` | Status | When |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Bean Validation failed on a body, parameter or path variable. Carries `errors`. |
| `BAD_REQUEST` | 400 | Missing or malformed JSON body, unparseable parameter, or any other 400 with no more specific code. |
| `UNAUTHORIZED` | 401 | No credentials on a protected endpoint, or an unauthenticated request rejected by the filter chain. |
| `INVALID_CREDENTIALS` | 401 | Login with a wrong `uniqueId`/password pair, a disabled account, or a wrong `currentPassword` on `change-password`. The four are deliberately indistinguishable on login. |
| `TOKEN_EXPIRED` | 401 | Access or refresh token is past its expiry. For an access token, refresh then retry; for the refresh token, log in again. |
| `TOKEN_INVALID` | 401 | Token is malformed, has a bad signature, was already rotated, or was revoked — including the whole family after a refresh-token reuse. Also returned when `/auth/refresh` is called with no cookie. Do not retry; log in again. |
| `FORBIDDEN` | 403 | Authenticated but lacks the required permission, or the object-level check failed (someone else's record). |
| `PASSWORD_CHANGE_REQUIRED` | 403 | `mustChangePassword` is true; only `change-password`, `me` and `logout` are allowed. |
| `NOT_FOUND` | 404 | No such resource, or no route matches the path. |
| `METHOD_NOT_ALLOWED` | 405 | Route exists, HTTP method does not. |
| `CONFLICT` | 409 | Uniqueness or state conflict, e.g. a duplicate key or a second receipt for one payment. |
| `PAYLOAD_TOO_LARGE` | 413 | Upload exceeds the configured limit. |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type` is not accepted for this endpoint. |
| `UNPROCESSABLE` | 422 | Well-formed request that breaks a business rule, e.g. marks above the exam maximum, or a new password identical to the current one. |
| `LOCKED` | 423 | Account locked after 5 failed logins, or an exam paper requested before its release time. Carries `retryAfterSeconds`. |
| `RATE_LIMITED` | 429 | Too many requests; may carry `retryAfterSeconds`. |
| `INTERNAL_ERROR` | 500 | Unhandled server failure. Carries `errorId`; body never exposes internals. |
| `DEPENDENCY_FAILED` | 502 | An upstream call failed — Razorpay, S3/MinIO, SMTP, the AI provider. |
| `FEATURE_DISABLED` | 503 | The endpoint is behind a feature flag that is off in this deployment, e.g. `app.features.ai`. |

Codes are additive: the frontend must have a fallback branch for a code it does not know.

## 4. Unique IDs

The format is `{SCHOOL_CODE}-{TYPE}-{YY}-{SEQ}`:
- `TYPE` is `STU` (5-digit sequence) or `EMP` (4-digit sequence). Teachers, staff and admins are all `EMP`.
- `YY` is the year of admission or joining.
- IDs are never reused. `SCHOOL_CODE` comes from the environment config.

The sequence is per type **and per year**, so it restarts at 1 each January: `DEMO-STU-26-00001`,
`DEMO-STU-27-00001`. Treat an ID as an opaque string — do not parse the sequence and do not assume
that a higher sequence means a later admission across different years. A sequence wider than its
padding is not truncated (`DEMO-EMP-26-123456` is valid), so do not rely on a fixed total length.

Receipt numbers (§ Payments) come from the same generator with the school's configured receipt prefix
instead of a `TYPE`: `{receiptPrefix}-{YY}-{SEQ}`, e.g. `DVM-RCP-26-000001`.

## 5. Endpoint map

The `Who` column gives access. "Self" means the user's own records only.

### Public / school
| Method | Path | Who |
|---|---|---|
| GET | `/public/school` | anyone. Landing-page projection, public-safe fields only (listed below) |
| GET | `/public/notices?page=&size=` | anyone. Notices flagged `isPublic`, published and unexpired; no attachments, no author |
| GET | `/school/config` | `SCHOOL_CONFIG_MANAGE` (ADMIN). The full configuration |
| PUT | `/school/config` | `SCHOOL_CONFIG_MANAGE` (ADMIN). Replaces the editable configuration |
| POST | `/school/config/images` | `SCHOOL_CONFIG_MANAGE` (ADMIN). `multipart/form-data` with `file` and `category` (`LOGO`, `FAVICON`, `GALLERY`) → `{url}` |

`GET /public/school` returns exactly these keys and no others:

```json
{
  "name": "Demo Vidya Mandir Senior Secondary School",
  "tagline": "Learn with curiosity, lead with character",
  "logoUrl": "https://media.example/public/logo/....png",
  "faviconUrl": "https://media.example/public/favicon/....ico",
  "theme": { "primary": "#0B3D91", "secondary": "#F2A93B", "accent": "#12B886" },
  "about": "...",
  "vision": "...",
  "principal": {
    "name": "Dr. Anita Deshpande",
    "designation": "Principal",
    "photoUrl": "https://media.example/public/gallery/principal.jpg",
    "message": "..."
  },
  "academics": {
    "board": "Madhya Pradesh Board of Secondary Education",
    "summary": "...",
    "levels": [
      { "name": "Primary", "range": "Classes I to V", "description": "..." },
      { "name": "Middle", "range": "Classes VI to VIII", "description": "..." }
    ]
  },
  "highlights": [
    { "title": "Thirty-five to a class", "description": "...", "icon": "users" },
    { "title": "Safe journeys", "description": "...", "icon": "bus" }
  ],
  "facilities": ["Library", "Science laboratories"],
  "galleryImageUrls": ["https://media.example/public/gallery/....jpg"],
  "stats": { "students": 1240, "teachers": 68, "years": 32, "passPercentage": 98 },
  "contact": {
    "addressLine1": "17 Shastri Marg", "addressLine2": "Near Civil Lines Post Office",
    "city": "Demo City", "state": "Madhya Pradesh", "postalCode": "462001",
    "phone": "+91 755 400 1200", "alternatePhone": "+91 98260 11223",
    "email": "office@example", "websiteUrl": "https://example"
  },
  "mapEmbedUrl": "https://www.google.com/maps/embed?pb=...",
  "socialLinks": { "facebook": "...", "instagram": "...", "youtube": "...", "twitter": null, "linkedin": null }
}
```

**Only `name`, `about`, `theme`, `stats` and `contact` are always present. Everything else is
optional and the key is omitted when the school has not filled it in** — including whole objects
(`principal`, `academics`) and whole arrays (`highlights`, `facilities`, `galleryImageUrls`). Fields
*inside* an optional object are independently optional too: a `principal` with only a `message` and no
`name` is valid and normal. Arrays may also be present but empty. The endpoint never fails because
content is missing, so render every section conditionally rather than assuming a shape.

| Field | Notes |
|---|---|
| `vision` | The school's vision statement. |
| `principal` | `{name, designation, photoUrl, message}`, all optional. `designation` is what to print under the name (`Principal`, `Director`, …). |
| `academics` | `{board, summary, levels}` — public, descriptive copy. `board` is worded by the school, not a code. |
| `academics.levels[]` | `{name, range, description}`, in display order. `name` is always present on a level that exists; `range` is free text (`Classes I to V`), not a parsable range. Purely descriptive — the real classes and sections come from the academics endpoints, and the two need not agree. |
| `highlights[]` | `{title, description, icon}` "why us" cards, in display order. `title` is always present on a highlight that exists. |
| `highlights[].icon` | A **Tabler icon name** the frontend resolves to a glyph, e.g. `school`, `users`, `bus`. The backend stores and returns it verbatim and never validates that the icon exists, so fall back to a default glyph on an unknown name. |

The receipt prefix, the receipt, salary-slip and report-card footers, attendance edit window, working
days, grading scheme and school code are **not** in the public projection; they are only in
`GET /school/config`.
Note that public `academics` (marketing copy) and internal `academicSettings` (how the school runs)
are different things and only the first is public.

`PUT /school/config` takes `{identity, landing, gradingScheme, academicSettings}` — the same shape
`GET /school/config` returns, minus the read-only `code` and `updatedAt`. The school code cannot be
changed through the API: it comes from `SCHOOL_CODE` and every unique ID already issued is built from
it. Grade bands must not overlap or repeat a label, and when `mode` is `GRADES` or `BOTH` they must
cover 0–100 with no gap; a scheme that breaks this returns 400 with `errors[].field` of
`gradingScheme.bands`. Two admins saving at once: the second gets 409 `CONFLICT`.

Image uploads accept PNG, JPEG, GIF, WebP and ICO, identified by the file's own bytes rather than its
`Content-Type`. SVG is rejected. The returned URL is public and immutable; put it into
`PUT /school/config` to actually use it.

### Academics
| Method | Path | Who |
|---|---|---|
| GET | `/sessions` | any authenticated user |
| POST | `/sessions` | `ACADEMICS_MANAGE` (ADMIN) |
| PUT | `/sessions/{id}/activate` | `ACADEMICS_MANAGE` (ADMIN). Exactly one session is active |
| GET | `/classes` | any authenticated user. Includes sections, subjects and assignments |
| POST | `/classes` | `ACADEMICS_MANAGE` (ADMIN) |
| PUT | `/classes/{id}` | `ACADEMICS_MANAGE` (ADMIN) |
| PUT | `/classes/{id}/assignments` | `ACADEMICS_MANAGE` (ADMIN). Class teacher and subject→teacher mapping |
| GET | `/subjects` | any authenticated user |
| POST | `/subjects` | `ACADEMICS_MANAGE` (ADMIN) |
| PUT | `/subjects/{id}` | `ACADEMICS_MANAGE` (ADMIN) |
| GET | `/users/teachers` | any authenticated user. The teacher picker: `[{uniqueId, name}]` |

Reading academics needs **no permission at all** — every logged-in user has to know what class 5-B
is. All four lists are small and unpaginated: a bare JSON array, not the `items`/`page` envelope.

**Sessions.** `{id, name, startDate, endDate, active}`. `name` is free text (`2026-27`,
`2026-2027`, `AY 2026/27` — the school decides) and unique; `endDate` must be after `startDate`.
Listed newest first.

`POST /sessions` takes `{name, startDate, endDate}` → 201. There is no `active` flag in the body:
`PUT /sessions/{id}/activate` is the only way to switch the current session, so a create cannot move
the whole school by accident. The one exception is the **first** session of a deployment, which is
activated on creation — a school with sessions but none current would break every module that asks
which year it is. A duplicate name is 409 `CONFLICT`.

`PUT /sessions/{id}/activate` stands every other session down in the same call, and activating the
session that is already active is a no-op rather than an error. Other modules read the active session
through an internal `AcademicContext`; until one exists, endpoints that need it answer 422
`UNPROCESSABLE`.

**Subjects.** `{id, name, code}`. Both `name` and `code` are unique; `code` is 2–16 letters, digits
or hyphens and is **stored and returned upper-case**, so posting `eng` after `ENG` is 409
`CONFLICT`. Classes refer to subjects by `id`, so renaming or recoding one does not change which
classes teach it. Listed by name.

**Classes.**

```json
{
  "id": "66f0...", "name": "Class 1", "order": 1,
  "sections": ["A", "B"],
  "subjectIds": ["66f1...", "66f2..."],
  "assignments": [
    { "section": "A", "classTeacher": "DEMO-EMP-26-0002",
      "subjectTeachers": [ { "subjectId": "66f1...", "teacher": "DEMO-EMP-26-0002" } ] }
  ]
}
```

`order` (0–1000) is what the list is sorted by, with `name` as a tie-break — names do not sort
usefully, since "Class 10" precedes "Class 2" alphabetically and schools mix in "Nursery" and "LKG".
Section names are **stored and returned upper-case** and are unique within the class. `assignments`
is always present and may be empty; a section with nobody assigned simply has no entry, and
`classTeacher` may be `null` on an entry that only maps subject teachers.

`POST /classes` and `PUT /classes/{id}` take `{name, order, sections, subjectIds}` — no
`assignments`, so editing a class name cannot silently wipe who teaches it. A `subjectId` that does
not exist is 400 `VALIDATION_ERROR` with `errors[].field` of `subjectIds`. An update that **removes**
a section or a subject drops the assignments that referred to it; everything else is kept.

`PUT /classes/{id}/assignments` takes `{assignments: [{section, classTeacher, subjectTeachers:
[{subjectId, teacher}]}]}` and is a **full replace, not a merge**: a section left out of the body
ends up with nobody assigned, so send the whole picture you want. Teachers are given by login
`uniqueId` — fetch the list from `GET /users/teachers` — and must be enabled accounts with the
TEACHER role; `classTeacher` may be omitted or `null`. Everything is validated before anything is
written, so an unknown section, a subject the class does not teach, a repeated section or subject, or
an id that is not an active teacher returns 400 `VALIDATION_ERROR` listing **every** problem, with
nothing changed.

Teachers are referenced by `uniqueId` throughout, which is the same id their employee record and
their login carry — the three are one identity and it is never reissued. Since B6 the picker at
`GET /users/teachers` reads the employee directory (`employeeType: TEACHER`, `status: ACTIVE`);
assignability is checked against the login, and the two stay in step because an employee set to
`LEFT` has their login disabled in the same call.

### Students
| Method | Path | Who |
|---|---|---|
| GET | `/students?classId=&section=&status=&q=&feeStatus=&page=&size=` | `STUDENT_READ_BASIC` or `STUDENT_READ_FULL` (ADMIN, TEACHER) |
| GET | `/students/me` | any authenticated user; 404 unless the login is a student's |
| GET | `/students/{uniqueId}` | ADMIN (full), TEACHER (teacher view), STUDENT (self only) |
| POST | `/students` | `STUDENT_WRITE` (ADMIN). Creates the login and returns the temporary password once |
| PUT | `/students/{uniqueId}` | `STUDENT_WRITE` (ADMIN) |
| PATCH | `/students/{uniqueId}/status` | `STUDENT_WRITE` (ADMIN). `ACTIVE`, `LEFT`, `ALUMNI` |
| POST | `/students/{uniqueId}/reset-password` | `STUDENT_WRITE` (ADMIN) |
| POST | `/students/import` (multipart .xlsx) | ADMIN. Returns `{created, failed: [{row, errors}]}` — **not built yet** |
| GET | `/students/import/template` | ADMIN. Downloads the .xlsx template — **not built yet** |
| POST | `/students/promote` | ADMIN. Bulk promotion to the next session/class — **not built yet** |

A student is addressed by `uniqueId` (`DEMO-STU-26-00142`), never by the Mongo `id`, and the
`uniqueId` never changes. Students are **never deleted** — marks, invoices and receipts point at
them — so leaving is `PATCH …/status`.

**Role-based projections.** Enforced on the backend as three unrelated response types, not by hiding
fields in the UI. `GET /students/{uniqueId}` returns whichever one fits the caller, so branch on the
keys you get rather than assuming a shape:
- `StudentAdminView` — every field, plus `id`, `createdAt` and `updatedAt`.
- `StudentTeacherView` — `uniqueId`, `name`, `dob`, `gender`, `photoUrl`, `className`, `section`,
  `rollNo`, `guardians` (names, `phone`, `altPhone`, `email`), `bloodGroup`, `feeStatus`. It has
  **no amounts, no payment history**, and no `address`, `admissionNo`, `previousSchool` or
  `occupation`. Attendance and marks join it in B8/B9.
- `StudentSelfView` — the student's own record: everything but the Mongo `id` and the timestamps.

`feeStatus` is `PAID` | `PARTIAL` | `DUE` | `OVERDUE` and carries no amount. It is now real — derived
from the student's invoices, with the rules under [Fees](#fees).

**Admission.** `POST /students` takes:

```json
{
  "name": "Aarav Sharma", "dob": "2015-06-14", "gender": "MALE",
  "photoUrl": null, "admissionNo": "2026/118", "admissionDate": "2026-04-05",
  "enrollment": { "classId": "66f0...", "section": "A", "rollNo": 12 },
  "guardians": { "fatherName": "...", "motherName": "...", "guardianName": null,
                 "phone": "+91 98260 11223", "altPhone": null, "email": null, "occupation": "..." },
  "address": { "line1": "...", "line2": null, "city": "...", "state": "...", "postalCode": "462001" },
  "bloodGroup": "O+", "previousSchool": null
}
```

→ 201 `{uniqueId, temporaryPassword, student}` where `student` is a `StudentAdminView`.

`enrollment.sessionId` is **not** in the body: it is always the active session, so a request cannot
file this year's admission under last year. 422 `UNPROCESSABLE` if no session is active.
`status` is not in the body either — a new student is `ACTIVE`, and the status endpoint is the only
way to move it, so editing a phone number cannot quietly re-admit somebody who left.

The `classId` must exist and must actually have that `section` → otherwise 400 `VALIDATION_ERROR` on
`enrollment.classId` / `enrollment.section`. A duplicate `admissionNo`, or a `rollNo` already used in
that (session, class, section), is 409 `CONFLICT`.

The student document and its login are written in **one transaction** and share one `uniqueId`:
either both exist or neither does. The login gets no e-mail address — students sign in with the
`uniqueId`, and siblings routinely share the family address.

**`temporaryPassword` is returned by `POST /students` and `POST …/reset-password` and nowhere else,
ever.** Ten characters, no `O`/`0`/`l`/`1`/`I`. Only its BCrypt hash is stored; it is never logged
and never written to the audit trail. `mustChangePassword` is set, so the first login must go
through `POST /auth/change-password` before anything else works. An admin who loses it issues
another — there is no way to read it back. A reset also revokes every refresh token that account
had.

**Listing.** All filters optional and ANDed. `q` is matched as an anchored **prefix** against the
name (case-insensitive), the `uniqueId` and the family phone number — not a substring search, so
"sharma" does not find "Aarav Sharma". Omitting `status` returns every status, including `LEFT` and
`ALUMNI`; ask for `status=ACTIVE` explicitly. Default sort is by name.

`feeStatus=PAID|PARTIAL|DUE|OVERDUE` filters on the **derived** status, not on a stored field, so it
is resolved against the invoices at request time; `totalItems` and the page boundaries are correct
for the filtered set. It composes with the other filters — `?classId=…&feeStatus=OVERDUE` is the
defaulters list for one class. Rows are one teacher-safe shape for both callers:

```json
{ "uniqueId": "...", "name": "...", "photoUrl": null, "classId": "...", "className": "Class 1",
  "section": "A", "rollNo": 12, "guardianPhone": "...", "status": "ACTIVE", "feeStatus": "DUE" }
```

`PUT /students/{uniqueId}` takes the same body as `POST` and replaces the editable detail; renaming
a student also renames their login, so `/auth/me` agrees. `PATCH …/status` takes
`{status, reason?}`; `reason` is kept only in the audit trail.

Create, update, status change and password reset are all written to `audit_logs`.

### Employees (teachers + staff)
| Method | Path | Who |
|---|---|---|
| GET | `/employees?type=&status=&q=&page=&size=` | `EMPLOYEE_READ` (ADMIN) |
| GET | `/employees/me` | any authenticated user; 404 unless the login is an employee's |
| GET | `/employees/{uniqueId}` | ADMIN, or that employee. Everyone else 403 |
| POST | `/employees` | `EMPLOYEE_WRITE` (ADMIN) |
| PUT | `/employees/{uniqueId}` | `EMPLOYEE_WRITE` (ADMIN) |
| PATCH | `/employees/{uniqueId}/status` | `EMPLOYEE_WRITE` (ADMIN). `ACTIVE`, `LEFT` |
| POST | `/employees/{uniqueId}/reset-password` | `EMPLOYEE_WRITE` (ADMIN) |
| GET | `/users/teachers` | any authenticated user. The teacher picker: `[{uniqueId, name}]` |
| POST | `/employees/import` | ADMIN — **not built yet** |

Teachers and staff are one collection. `employeeType` is `TEACHER` or `STAFF` and decides which half
of the record is live — the other half is **cleared, not rejected**, so changing a teacher to staff
drops their `qualification`, `subjectIds` and `experienceYears`, and the reverse drops
`designation`:

| Field | Applies to |
|---|---|
| `qualification`, `subjectIds`, `experienceYears` | TEACHER only; null on staff |
| `designation` (free text: Accountant, Driver, Peon…), `hasLogin` | STAFF only; a teacher always has a login |

`subjectIds` are what a teacher *can* teach; which class they actually take is set by
`PUT /classes/{id}/assignments`. Every id must exist → otherwise 400 on `subjectIds`.

**Logins.** A TEACHER always gets one (role TEACHER). A STAFF member gets one only when
`hasLogin` is true, and it may be switched on **later**: `PUT /employees/{uniqueId}` with
`hasLogin: true` creates the account then and returns its `temporaryPassword` in that response.
`hasLogin` is never switched back off by an update — removing access is what the status endpoint is
for. `POST` and `PUT` therefore share one response shape:

```json
{ "uniqueId": "DEMO-EMP-26-0007", "temporaryPassword": "Kp7fQm2xTz", "employee": { ... } }
```

`temporaryPassword` is **null when this call created no login** — a staff member without
`hasLogin`, or any update that did not turn it on. The same once-only rules as for students apply:
ten characters, no `O`/`0`/`l`/`1`/`I`, BCrypt hash only, never logged or audited,
`mustChangePassword` set. 400 if you reset the password of an employee that has no login.

Unlike a student's, an employee's `email` **is** their login's address, so two employees cannot
share one → 409 `CONFLICT`.

**Status.** `PATCH …/status` takes `{status, reason?}` and enables or disables the login to match;
`LEFT` also ends their sessions. The two move together on purpose — a teacher who has left but can
still sign in keeps access to marks and exam papers, and would still be assignable to a section.
Employees are never deleted: payroll runs and salary slips point at them.

**Bank details and PAN.** `bank` is `{accountName, accountNumber, ifsc, bankName}` on the way in,
with `accountNumber` 6–20 digits. The account number is stored **AES-256-GCM encrypted** under
`ENCRYPTION_KEY`, alongside its last four digits in the clear. Only `panLast4` is ever held — never
the full PAN.

Who sees what:
- `GET /employees` rows carry `bankAccountLast4` (e.g. `"4821"`) and never the number. The UI
  formats it; a list of fifty employees is not fifty decryptions.
- `GET /employees/{uniqueId}` returns `bank.accountNumber` **decrypted**, to an ADMIN or to that
  employee for their own record. There is no third projection, which is why anybody else gets 403
  rather than a thinner view.
- Audit entries redact it, and it is never logged.

Omitting `bank` from a `PUT` **keeps** the details already on file rather than wiping them. If
`ENCRYPTION_KEY` is unset, only requests that actually carry bank details fail, and the error says
so — everything else works without it.

**Listing.** All filters optional and ANDed. `q` is an anchored **prefix** against the name
(case-insensitive), the `uniqueId` and the phone number. Omitting `status` returns `LEFT` employees
too; ask for `status=ACTIVE` explicitly. Default sort is by name.

`GET /users/teachers` is unchanged in shape and path, but since B6 it reads the **employee
directory** — `employeeType: TEACHER` with `status: ACTIVE` — rather than the login table.

Create, update, status change and password reset are all written to `audit_logs`.

### Member search
| Method | Path | Who |
|---|---|---|
| GET | `/members/{uniqueId}` | ADMIN, TEACHER. Resolves STU or EMP and returns `{memberType, data}` projected for the caller. A teacher searching an EMP id gets 403 unless it is their own |
| GET | `/members/search?q=` | ADMIN, TEACHER. Type-ahead by name or uniqueId, max 10 results |

### Attendance
| Method | Path | Who |
|---|---|---|
| GET | `/holidays?from=&to=` | any authenticated user |
| POST | `/holidays` | `HOLIDAY_MANAGE` (ADMIN). Body `{date, name}` |
| DELETE | `/holidays/{id}` | `HOLIDAY_MANAGE` (ADMIN) → 204 |
| GET | `/attendance/class/{classId}/{section}?date=` | `ATTENDANCE_MARK_STUDENT` (ADMIN, TEACHER) |
| PUT | `/attendance/class/{classId}/{section}?date=` | same. Body `{entries:[{studentUniqueId, status}]}` |
| GET | `/attendance/students/{uniqueId}?from=&to=` | ADMIN, TEACHER, or that student |
| GET | `/attendance/employees?date=` | `ATTENDANCE_MARK_EMPLOYEE` (ADMIN) |
| PUT | `/attendance/employees?date=` | same. Body `{entries:[{employeeUniqueId, status}]}` |
| GET | `/attendance/employees/{uniqueId}?from=&to=` | ADMIN, or that employee |

Status is `PRESENT`, `ABSENT`, `LATE`, `HALF_DAY` or `LEAVE`, for students and employees alike.
**There is no `HOLIDAY` status** — a holiday is a property of the day, not of a person, and lives in
`/holidays`; attendance simply cannot be marked on one.

**Holidays** are `{id, date, name}`, one per date (a second is 409). The weekly closure is *not* in
here: which days of the week the school works is `academicSettings.workingDays` in
`GET /school/config`, and repeating every Sunday as a holiday document would be a drifting second
copy of it. Both ends of `?from=&to=` are inclusive.

**The class register.** `GET` returns the current roll of the class-section in roll order, each
student with their status for that date or `null` if unmarked:

```json
{ "classId": "66f0...", "className": "Class 1", "section": "A", "date": "2026-07-14",
  "marked": true, "editable": false, "lockedReason": "This register was submitted on … and the 48-hour correction window has closed. Ask an administrator to change it.",
  "markedBy": "DEMO-EMP-26-0002", "markedAt": "2026-07-14T04:12:00Z",
  "entries": [ { "studentUniqueId": "DEMO-STU-26-00001", "name": "Aarav Sharma", "rollNo": 12, "status": "PRESENT" } ] }
```

Rows come from the **current roll**, not from the stored document, so a student admitted since the
day was marked appears with a `null` status rather than being invisible. Check `editable` before
offering a form; the backend re-checks it on the `PUT` regardless.

`PUT` is a **full replace**: a student left out of `entries` ends up unmarked. **Any teacher may
mark any class** — schools move cover around, and a class-teacher-only rule means the register goes
unmarked the day they are off. Entries must name ACTIVE students of that class-section, once each,
or 400 with every offending entry listed and nothing written.

**When a register may be written**, checked in this order and reported as 422 `UNPROCESSABLE`:

1. not a future date;
2. a working day per `academicSettings.workingDays`;
3. not a holiday;
4. for a teacher only, inside `academicSettings.attendanceEditWindowHours` (seeded at 48).

The window runs **from `markedAt`** — the moment the register was last submitted — which is what
makes it a correction window. A register never submitted has no such moment, so for a *first*
submission it runs from the **start of the day being marked**. With 48 hours: a teacher has until
the end of the day after to get Monday's register in, and then 48 hours from each correction to fix
it. `ATTENDANCE_CORRECT_ANY` (ADMIN) is exempt from step 4 entirely.

**Per-person history.** `GET /attendance/students/{uniqueId}` and
`/attendance/employees/{uniqueId}` share one shape:

```json
{ "uniqueId": "...", "name": "...", "from": "2026-07-01", "to": "2026-07-31",
  "days": [ { "date": "2026-07-14", "status": "PRESENT" } ],
  "summary": { "present": 18, "absent": 2, "late": 1, "halfDay": 2, "leave": 1,
               "workingDays": 23, "percentage": 86.96 } }
```

Only days that were actually **marked** appear in `days`. Holidays, weekends and days nobody
submitted are absent, and do not count against anybody — a register nobody filled in says nothing
about whether somebody was there.

`workingDays` is **the number of marked days excluding `LEAVE`** — the denominator — *not* the
number of working days in the calendar range. `percentage = (present + late + 0.5 × halfDay) /
workingDays × 100`, to two decimals, and `0` when nothing counted. `LATE` therefore costs nothing
but is still on the record; `LEAVE` leaves the denominator rather than counting against the person.

Every `PUT` is written to `audit_logs` with the before and after entry lists.

### Exams & marks
| Method | Path | Who |
|---|---|---|
| GET | `/exams?sessionId=&classId=` | any authenticated user; a student sees less (below) |
| POST | `/exams` | `EXAM_MANAGE` (ADMIN) |
| PUT | `/exams/{id}` | `EXAM_MANAGE` (ADMIN). **Only while `DRAFT`** |
| PUT | `/exams/{id}/status` | `EXAM_MANAGE` (ADMIN). Body `{status}` |
| GET | `/exams/{examId}/marks?classId=&section=&subjectId=` | `MARKS_WRITE` (ADMIN, TEACHER) |
| PUT | `/exams/{examId}/marks?classId=&section=&subjectId=` | same. Only while `MARKS_ENTRY` |
| GET | `/exams/{examId}/results?classId=&section=` | ADMIN, TEACHER |
| GET | `/students/{uniqueId}/results?sessionId=` | ADMIN, TEACHER, or that student |
| GET | `/students/{uniqueId}/report-card/{examId}.pdf` | ADMIN, TEACHER, or that student |

**Status is the gate for everything else.** `DRAFT` → `MARKS_ENTRY` → `PUBLISHED`, and back the
same way; `DRAFT` straight to `PUBLISHED` is refused (nothing to publish). The schedule is editable
only in `DRAFT` — once marks exist, changing what a paper is out of would silently invalidate them —
and marks are writable only in `MARKS_ENTRY`. The way back from `PUBLISHED` exists so a mistake
found after release is fixable; it hides results from students again while you correct them.

**Publishing requires a complete mark sheet.** Every ACTIVE student of every class sitting the exam
must have a mark in every scheduled subject. An absent-flagged mark counts as marked — an absence is
a recorded outcome, not a gap. When something is missing it answers **422 with a `missing` array**
rather than a bare refusal, so you can chase exactly those teachers:

```json
{ "code": "UNPROCESSABLE", "detail": "This exam cannot be published: 3 mark(s) have not been entered.",
  "missing": [ { "studentUniqueId": "DEMO-STU-26-00007", "studentName": "Aarav Sharma",
                 "className": "Class 1", "section": "A", "subjectId": "66f1...", "subjectName": "Maths" } ] }
```

**Exam.** `{id, name, sessionId, classIds, schedule, status}`, with `schedule` entries
`{subjectId, subjectName, date, maxMarks, passMarks}`. One exam spans several classes, because the
half-yearly is one event even though Class 1 and Class 5 sit different papers. `maxMarks` and
`passMarks` are per paper, not per subject — the same subject is worth 80 in one exam and 100 in
another. `sessionId` is **not** in the request body: an exam always belongs to the active session.
Names are unique within a session. `POST`/`PUT` take `{name, classIds, schedule}` and every id must
exist; `passMarks` must not exceed `maxMarks`.

`GET /exams` defaults to the active session. ADMIN and teachers see every status. **A student sees
only `PUBLISHED` exams, and only those their own class sits** — the `classId` parameter is ignored
for them in favour of their own enrollment.

**Marks are whole numbers.** `marksObtained` is an integer between 0 and the paper's `maxMarks`;
there is no half-mark support. `GET` returns the grid — every ACTIVE student of the class-section in
roll order with their mark or `null` — built from the **current roll**, so a student admitted since
the grid was last saved appears unmarked rather than invisibly.

`PUT` is an **upsert per row, not a replace**: a student left out of `entries` keeps the mark they
had, so entering half a class does not wipe the other half. (This is deliberately the opposite of
the attendance register, where leaving somebody out unmarks them.) Body is
`{entries:[{studentUniqueId, marksObtained, absent, remarks}]}`. `absent: true` stores
`marksObtained` as null whatever you send. **Any teacher may enter marks for any class.** Everything
is validated first, so one out-of-range mark fails the request and writes nothing.

**Results.** Per subject `{subjectId, subjectName, marks, maxMarks, passMarks, absent, grade, pass,
remarks}`; per exam `{total, maxTotal, percentage, grade, remark, rank, rankOutOf, result}`.

- `total` sums the marks; an absent or unmarked paper contributes 0.
- `maxTotal` sums `maxMarks` over **every** paper in the schedule, marked or not — a missing entry
  lowers the percentage rather than quietly shrinking the exam.
- `percentage` = `total / maxTotal × 100`, to two decimals.
- `grade` comes from `gradingScheme.bands` in `GET /school/config`, looked up on the **floored**
  percentage: 89.6% is graded as 89, because a band starting at 90 means ninety. The displayed
  `percentage` is still 89.6. `grade` is `null` if the scheme leaves a gap there rather than failing.
- A subject is **failed if absent, unmarked, or below `passMarks`**. `result` is `PASS` only when
  every subject passed.
- `rank` is within the student's own **class-section**, by `total`, highest first. Ties share a rank
  and the next rank skips: 1, 2, 2, 4. `rankOutOf` is the size of that section.

`GET /students/{uniqueId}/results` defaults to the active session. A student reading their own sees
only `PUBLISHED` exams; ADMIN and teachers see `MARKS_ENTRY` ones too, so a sheet can be checked
before release. `GET /exams/{examId}/results` is the class sheet: one row per student, best rank
first, with `marks` as one entry per column in schedule order (`null` where unmarked or absent).

**Report card PDF.** `GET /students/{uniqueId}/report-card/{examId}.pdf` answers
`application/pdf` with `Content-Disposition: inline` and a filename like
`report-card-DEMO-STU-26-00007-half-yearly.pdf`, so it can be opened in an `<iframe>` or a new tab
and printed; there is no JSON variant. Who may read it is the same rule as
`/students/{uniqueId}/results`, but **`PUBLISHED` only, for an admin as much as for the student** —
an exam still in marks entry has no result to state, and 422 `UNPROCESSABLE` says so. A student whose
class does not sit that exam gets 400, and one with no current enrollment 422.

One A4 page: the logo, name and address from the configuration, the student's name, unique ID, class,
section and roll no, a subject table (marks, max, grade, pass/fail — `AB` for a recorded absence and
a dash for a paper nobody marked), the totals, percentage, grade, rank and result, attendance for the
**whole session** (not up to the exam), signature lines for the class teacher and the principal, and
`academicSettings.reportCardFooter`. A missing logo, class teacher, principal or footer is simply
left out; the card is never refused over branding.

Marks entry and every status change are written to `audit_logs`.

### Exam papers (vault)
| Method | Path | Who |
|---|---|---|
| POST | `/exam-papers` (multipart) | ADMIN, TEACHER. Fields: examId, subjectId, classId, `releaseAt` |
| GET | `/exam-papers?examId=` | ADMIN, TEACHER. Returns metadata only |
| GET | `/exam-papers/{id}/download` | ADMIN at any time; TEACHER only after `releaseAt`. Otherwise 423 `locked-resource`. Returns a 5-minute pre-signed URL. Every access is audited |

### Quizzes
| Method | Path | Who |
|---|---|---|
| GET/POST/PUT | `/quizzes` | write: ADMIN, TEACHER; read: STUDENT sees quizzes for their class only |
| POST | `/quizzes/{id}/publish` | ADMIN, TEACHER |
| POST | `/quizzes/{id}/attempts` | STUDENT. Starts an attempt and returns questions **without answers** |
| PUT | `/quizzes/attempts/{attemptId}` | STUDENT. Submits answers; auto-graded; submissions are rejected after the time limit plus 30s grace |
| GET | `/quizzes/{id}/results` | ADMIN, TEACHER |
| GET | `/quizzes/attempts/mine` | STUDENT |

### Notices
| Method | Path | Who |
|---|---|---|
| GET | `/notices?page=&size=` | `NOTICE_READ` (everybody). Filtered to the caller's audience |
| POST | `/notices` | `NOTICE_WRITE_ALL` (ADMIN, any audience) or `NOTICE_WRITE_CLASS` (TEACHER, `CLASS` only) |
| PUT | `/notices/{id}` | same, and a teacher only for notices they wrote |
| DELETE | `/notices/{id}` | same. 204, no body |
| POST | `/notices/attachments` | ADMIN, TEACHER. `multipart/form-data` with `file` → `{name, url, size}` |
| GET | `/notices/attachments/{key}` | `NOTICE_READ` (everybody). Streams the file |
| GET | `/public/notices?page=&size=` | anyone, no auth. Public notices only |

**Notice.** `{id, title, body, audience, classId, section, isPublic, pinned, attachments, publishAt,
expiresAt, authorUniqueId, authorName, createdAt}`, with `attachments` entries `{name, url, size}`.
`POST`/`PUT` take everything but the `author*` fields, the id and `createdAt`.

**`body` is plain text, not HTML.** Line breaks are preserved (line endings normalized to `\n`) and
nothing is interpreted as markup. Render it with `white-space: pre-wrap` and **never** as
`innerHTML`: the backend does not sanitize it, because it does not treat it as markup at all.

**Audience** is one of `ALL`, `STUDENTS`, `TEACHERS`, `STAFF`, `CLASS` — a plain enum, not the
`CLASS:{classId}:{section}` string of earlier drafts. For `CLASS`, `classId` is required and
`section` is optional (null means the whole class, and a section must be one the class actually has);
for every other audience both must be empty, or the request is 400.

**The publication window.** `publishAt` defaults to now, so the office can write a notice on Monday
for Friday; `expiresAt` is optional, must be after `publishAt`, and makes the notice disappear by
itself. Neither feed ever returns a notice outside its window.

**Who sees what** — applied as a query, so a notice you are not addressed by is never fetched:

| Caller | Sees |
|---|---|
| ADMIN | every notice |
| TEACHER | `ALL`, `TEACHERS`, and `CLASS` notices **they wrote** |
| STAFF | `ALL` and `STAFF` |
| STUDENT | `ALL`, `STUDENTS`, and `CLASS` notices for their own class whose `section` is theirs or null |

**Order is fixed: pinned first, then newest by `publishAt`.** A `sort` parameter is ignored — the
board has one order, and a client that could re-sort it would quietly bury a pinned notice. `page`
and `size` work as everywhere else.

**Writing.** An admin may post to any audience and may set `isPublic` and `pinned`. A teacher may
only post `CLASS` notices and gets **403** for any other audience, or for `isPublic`/`pinned` set to
true; they may edit and delete only their own, and an admin's edit never reassigns the author. Any
teacher may post to any class — the same stance as marks entry, where any teacher may enter marks for
any class.

**Attachments.** `POST /notices/attachments` takes one file, up to 10 MB, and accepts **PDF, JPEG and
PNG only**, identified by the file's own leading bytes rather than its `Content-Type` or extension.
Files are stored **privately**, outside the public prefix, so the returned `url` is this API's own
`GET /notices/attachments/{key}` — which streams the bytes to any logged-in caller holding the key
(an unguessable UUID), with `Content-Disposition: inline` and the uploader's file name. Uploading
alone stores nothing: put the returned object into the notice's `attachments` array, which rejects
any URL this application did not issue. Deleting a notice leaves its files in storage.

Create, update and delete are written to `audit_logs`, with the deleted notice kept in `before`.

### Fees
| Method | Path | Who |
|---|---|---|
| GET | `/fees/heads?active=` | `FEE_MANAGE` (ADMIN) |
| POST | `/fees/heads` | `FEE_MANAGE` (ADMIN) |
| PUT | `/fees/heads/{id}` | `FEE_MANAGE` (ADMIN) |
| GET | `/fees/structures?sessionId=&classId=` | `FEE_MANAGE` (ADMIN) |
| GET | `/fees/structures/{id}` | `FEE_MANAGE` (ADMIN) |
| POST | `/fees/structures` | `FEE_MANAGE` (ADMIN) |
| PUT | `/fees/structures/{id}` | `FEE_MANAGE` (ADMIN) |
| POST | `/fees/structures/{id}/generate-invoices` | `FEE_MANAGE` (ADMIN). Idempotent |
| GET | `/fees/concessions?studentUniqueId=` | `FEE_MANAGE` (ADMIN) |
| POST | `/fees/concessions` | `FEE_MANAGE` (ADMIN) |
| POST | `/fees/concessions/{id}/apply` | `FEE_MANAGE` (ADMIN) |
| GET | `/fees/students/{uniqueId}` | ADMIN, or that student. **A teacher gets 403** |
| GET | `/fees/students/{uniqueId}/status` | `FEE_READ_STATUS` (ADMIN, TEACHER) for anybody; a student for themselves |
| POST | `/fees/offline-payment` | `FEE_MANAGE` (ADMIN). Cash, cheque, UPI or transfer taken at the counter |
| GET | `/fees/reports/collection?from=&to=` | `FEE_MANAGE` (ADMIN) |
| GET | `/fees/reports/defaulters?sessionId=&classId=` | `FEE_MANAGE` (ADMIN) |

**Every amount in this section is an integer of paise** — `150000` is ₹1,500.00. There are no decimals
anywhere, in requests or responses (CLAUDE.md rule 3). Amounts a client sends are only ever *setup*
figures (a structure's items, a concession's value); what a family owes is always computed on the
server from the invoices.

**Fee head.** `{id, name, active, createdAt, updatedAt}`; `POST`/`PUT` take `{name, active?}`.
Names are unique → 409. `active` defaults to true on create and is left as-is when omitted on a
`PUT`. **There is no delete**: invoices name their heads, and a receipt that says "Transport" has to
keep saying so, so a head no longer charged is `active: false`. `GET` returns every head including
retired ones; pass `?active=true` for the ones a form should offer.

#### Fee structures
One structure says what **one class pays in one session**, split into installments.
`POST`/`PUT` take:

```json
{
  "classId": "66f0...",
  "installments": [
    { "name": "Q1", "dueDate": "2026-04-10",
      "items": [ { "headId": "66f1...", "amount": 1200000 },
                 { "headId": "66f2...", "amount": 300000 } ] }
  ],
  "lateFine": { "type": "PER_DAY", "amount": 2000, "graceDays": 7, "cap": 50000 }
}
```

→ `{id, sessionId, classId, className, installments, lateFine, yearTotal, createdAt, updatedAt}`,
where each installment comes back as `{name, dueDate, items:[{headId, headName, amount}], total}`.
Totals are pre-computed so the frontend never adds money up itself.

- `sessionId` is **not** in the body: a structure always belongs to the active session, the same
  stance as exams. 422 `UNPROCESSABLE` if no session is active.
- **One structure per (session, class)**, enforced by a unique index → 409 `CONFLICT` on a second
  one. "What does Class 5 pay this year" has to have exactly one answer.
- Every `headId` must exist **and still be active**, installment names must be unique within the
  structure, and no head may be charged twice in one installment → otherwise 400 `VALIDATION_ERROR`
  listing every problem at once.
- `lateFine` is optional; `null` means the school charges nothing for paying late.
- On a `PUT` the `classId` must match the structure being edited — a structure cannot be moved to
  another class, because the invoices generated from it name the old one (422). An installment that
  **already has invoices cannot be renamed or dropped** (422): its name is part of the idempotency
  key, so a rename would bill those students a second time under the new name.
- Editing amounts or due dates does **not** rewrite invoices already generated — an invoice is a
  statement of what was charged. Editing `lateFine` **does** affect every invoice immediately,
  because the fine is read live rather than stored.

#### Generating invoices
`POST /fees/structures/{id}/generate-invoices` → `{"created": 160, "skipped": 0}`.

One invoice per **ACTIVE** student enrolled in that class in that session, per installment, with that
student's concessions applied. **Idempotent**, via a unique index on
`(studentUniqueId, structureId, installmentName)`: a row that already exists is counted in `skipped`
rather than billed again, so `{"created": 0, "skipped": 160}` is the normal answer to a second call
and means "nothing left to do". Running it again after admitting a student writes only that student's
missing invoices. `LEFT` and `ALUMNI` students are never billed. The counts and the structure are
written to `audit_logs` as `FEE_INVOICES_GENERATED`.

#### Invoice
Returned by `/fees/students/{uniqueId}`, never created directly:

```json
{ "id": "66f3...", "studentUniqueId": "DEMO-STU-26-00142", "classId": "66f0...",
  "sessionId": "66ef...", "structureId": "66f2...", "installmentName": "Q1",
  "dueDate": "2026-04-10", "items": [ { "headId": "66f1...", "headName": "Tuition", "amount": 1200000 } ],
  "grossAmount": 1500000, "concessionAmount": 150000, "netAmount": 1350000,
  "paidAmount": 0, "lateFinePaid": 0, "lateFineDue": 14000, "balance": 1364000,
  "overdue": true, "status": "UNPAID" }
```

- `status` is `UNPAID` | `PARTIAL` | `PAID` | `CANCELLED`. An invoice whose `netAmount` is 0 — a full
  waiver — is raised as `PAID`. `CANCELLED` is how an invoice stops counting (a student who left,
  or one raised by mistake); it is still listed as history but is left out of every total and accrues
  no fine. Invoices are never deleted.
- `headName` is **copied onto the invoice** at generation, not resolved on read: renaming a head two
  years later must not change what a receipt already issued says it was for.
- `netAmount` = `grossAmount − concessionAmount`. `balance` = `netAmount − paidAmount + lateFineDue`,
  which is the figure to collect today.
- **`lateFineDue` is computed on every read and is never stored** — a figure that depends on today's
  date would be wrong by tomorrow. Nothing is charged until **`dueDate` + `graceDays`** has passed;
  from the day after that, `FLAT` charges `amount` once and `PER_DAY` charges `amount × days late`,
  capped at `cap` when `cap > 0` (`0` means uncapped). Whatever has already been collected as a fine
  (`lateFinePaid`) is taken off, and a settled or cancelled invoice accrues nothing. The rule is read
  **live from the structure**, so correcting the grace days corrects every invoice at once.
- `overdue` is "past `dueDate` + `graceDays` with something still owed", independent of whether the
  school charges a fine at all.

#### Concessions
`POST /fees/concessions` takes
`{studentUniqueId, type, value, headIds?, reason}` → `{id, studentUniqueId, studentName, sessionId,
type, value, headIds, reason, createdAt}`.

- `type` is `PERCENT` (`value` is a whole percentage, 1–100) or `FIXED` (`value` is paise, taken off
  each installment it applies to). A `PERCENT` over 100 is 400.
- `headIds` **empty or omitted means every head**, so a full staff-ward waiver is written once rather
  than listed head by head and then forgotten when a new head is added. Ids given must exist;
  retired heads are allowed here, unlike on a structure.
- `reason` is required — a discount with no stated reason is unauditable.
- `sessionId` is not in the body: a concession is always granted for the active session, because that
  is how a school reviews them. It does not follow the student into next year.
- Each concession is measured against **only the items it covers**: 50% off Transport takes half the
  bus fare, not half the bill. Several concessions add up, and the total is capped at the gross, so a
  family is never billed a negative amount however the waivers were stacked. `PERCENT` divides in
  integer paise and so rounds **down**.

**A concession applies to invoices generated afterwards.** Invoices that already exist are untouched,
so a discount can never silently restate a bill a family has been shown.
`POST /fees/concessions/{id}/apply` → `{"recalculated": 3, "skipped": 1}` is the explicit way to pull
it back over them: it rewrites `concessionAmount` and `netAmount` on that student's **`UNPAID`**
invoices for that session, recomputed from *all* of their concessions (so applying two in either
order gives the same answer). `PARTIAL`, `PAID` and `CANCELLED` invoices are deliberately left alone
— restating a bill already paid into would leave the receipt and the invoice disagreeing, and that is
a credit note rather than an edit. `skipped` counts `UNPAID` invoices that already came to the same
figure. Each rewritten invoice gets its own `FEE_INVOICE_RECALCULATED` audit entry with before and
after.

#### A student's fees
`GET /fees/students/{uniqueId}` — for an **admin, or that student** (which is also how a parent sees
it, signed in as the student). **A teacher gets 403**, not a thinned-out copy: amounts and payment
history are not theirs to see (CLAUDE.md rule 2), and `/status` is the endpoint they have.

```json
{ "studentUniqueId": "DEMO-STU-26-00142", "name": "Aarav Sharma", "classId": "66f0...",
  "className": "Class 1", "section": "A", "feeStatus": "OVERDUE",
  "invoices": [ … ],
  "totals": { "totalNet": 5400000, "totalPaid": 1350000, "lateFineDue": 14000, "balance": 4064000 } }
```

`invoices` is oldest due date first and **includes** cancelled ones as history. `totals` covers every
invoice that is not cancelled: `totalPaid` is principal only (fines taken are `lateFinePaid` per
invoice), and `balance` = `totalNet − totalPaid + lateFineDue`.

#### Fee status
`GET /fees/students/{uniqueId}/status` → `{studentUniqueId, feeStatus}` and nothing else — the
response has no field for an amount at all. ADMIN and teachers may ask about anybody; a student only
about themselves (403 otherwise); STAFF has no `FEE_READ_STATUS` and gets 403. This is the same value
that appears as `feeStatus` on `/students` rows and on the student projections.

Tested in this order, over every invoice that is not cancelled:

| | |
|---|---|
| `PAID` | nothing outstanding — **including a student with no invoices at all**, who has no balance |
| `OVERDUE` | something still owed past its `dueDate` + `graceDays` |
| `PARTIAL` | something has been paid and a balance remains |
| `DUE` | nothing paid yet, nothing past its due date |

`OVERDUE` beats `PARTIAL` on purpose: a part-paid invoice that is late is still late, and late is the
actionable answer.

#### Defaulters
`GET /fees/reports/defaulters?sessionId=&classId=` → an array, **not** paged. Defaults to the active
session; omit `classId` for the whole school.

```json
[ { "studentUniqueId": "DEMO-STU-26-00142", "name": "Aarav Sharma", "classId": "66f0...",
    "className": "Class 1", "section": "A", "balance": 4064000, "lateFineDue": 14000,
    "oldestDueDate": "2026-04-10", "invoiceCount": 3 } ]
```

`balance` includes the late fine running right now. Rows are ordered by **`oldestDueDate`, earliest
first** — the office chases whoever has been behind longest, not whoever owes most — with the larger
balance first on a tie. Only `UNPAID` and `PARTIAL` invoices count. A student whose record has since
been removed is still listed, by id, with a null `name`: money owed does not stop being owed because
the roll was edited.

#### Recording a payment taken at the counter
`POST /fees/offline-payment` takes:

```json
{ "studentUniqueId": "DEMO-STU-26-00142", "invoiceIds": ["66f3...", "66f4..."],
  "amount": 1364000, "mode": "CHEQUE", "reference": "004213",
  "payerName": "Rajesh Sharma", "payerRelation": "FATHER", "paidAt": "2026-04-20T05:30:00Z" }
```

→ 201 with a `Payment` (below).

- `mode` is `CASH`, `CHEQUE`, `UPI_OFFLINE` or `BANK_TRANSFER`. **`ONLINE` is rejected here** (400) —
  that is the gateway's flow in B12.
- `reference` — cheque number, UTR, UPI reference — is **required for every mode but `CASH`** (400),
  because a transfer nobody can trace is not reconcilable.
- `paidAt` is optional and defaults to now. Send it when the money arrived on a different day from
  the one it is being entered on: the collection report groups on this, not on when the row was
  written.
- `payerRelation` is `SELF`, `FATHER`, `MOTHER`, `GUARDIAN` or `OTHER`. There are no parent accounts,
  so `payerName` is the only record of who actually paid, and it is printed on the receipt.

**The amount is checked, never trusted** (CLAUDE.md rule 4). The server works out what the selected
invoices come to — outstanding principal plus the live late fine — and **refuses anything more than
that** with 422, rather than holding the excess as a credit nobody can account for later. Less is
fine. An invoice id that is not that student's, or that has been cancelled, is 400 naming the
offending index.

**Allocation order is fixed: oldest `dueDate` first, and within an invoice the late fine before the
fee.** Paying the fine first is what stops a part payment from leaving a fine quietly growing on an
invoice the family thought they had dealt with. A short payment leaves the invoice `PARTIAL`; a
payment that covered only the fine leaves it `UNPAID`, because none of the fee has been paid.

Everything happens in **one transaction** — the invoice updates, the receipt number and the payment
row either all land or none do. A receipt number handed to a family with no payment behind it, and
invoices marked paid with no record of who paid, are both worse than a failed request.

**`receiptNo` is `{receiptPrefix}-{YY}-{SEQ}`** — `DVM-RCP-26-000001` — with `receiptPrefix` from
`academicSettings` and a six-digit sequence per year. Numbers are never reused. 422 if no receipt
prefix is configured.

### Payments
| Method | Path | Who |
|---|---|---|
| GET | `/payments?studentUniqueId=` | `FEE_READ_FULL` (ADMIN) |
| GET | `/payments/mine` | `FEE_PAY_SELF` (STUDENT) |
| GET | `/payments/{id}` | ADMIN, or the student the payment is for. **A teacher gets 403** |
| GET | `/payments/{id}/receipt.pdf` | ADMIN, or the student the payment is for. **A teacher gets 403** |
| POST | `/payments/orders` | `FEE_PAY_SELF` (STUDENT). See [Payments (online)](#payments-online) |
| POST | `/payments/verify` | `FEE_PAY_SELF` (STUDENT). See [Payments (online)](#payments-online) |
| POST | `/public/payments/webhook` | Razorpay only. Signature-verified |

**Payment.** Counter payments and gateway payments share one collection and one shape, so an online
payment produces the same receipt through the same code:

```json
{ "id": "66f9...", "studentUniqueId": "DEMO-STU-26-00142", "mode": "CHEQUE",
  "amount": 1364000, "principalAmount": 1350000, "lateFineAmount": 14000,
  "invoiceIds": [ "66f3..." ],
  "allocations": [ { "invoiceId": "66f3...", "installmentName": "Q1", "dueDate": "2026-04-10",
                     "amount": 1350000, "lateFine": 14000,
                     "heads": [ { "headId": "66f1...", "headName": "Tuition", "amount": 1080000 },
                                { "headId": "66f2...", "headName": "Transport", "amount": 270000 } ] } ],
  "reference": "004213", "payerName": "Rajesh Sharma", "payerRelation": "FATHER",
  "receiptNo": "DVM-RCP-26-000001", "receiptUrl": "/api/v1/payments/66f9.../receipt.pdf",
  "status": "CAPTURED", "recordedBy": "DEMO-EMP-26-0001", "paidAt": "2026-04-20T05:30:00Z" }
```

- `status` is `CREATED` | `CAPTURED` | `FAILED`. An offline payment is `CAPTURED` immediately — the
  clerk has the money. `CREATED` only arises in the gateway flow.
- An online payment carries four more fields, all **absent for a counter payment**: `gatewayOrderId`,
  `gatewayPaymentId`, `failureReason` (only when `FAILED`), and `unallocatedAmount` — see
  [Payments (online)](#payments-online).
- `allocations[].amount` is the part that settled the fee and `lateFine` the part that settled the
  fine; the two sum to what landed on that invoice, and across allocations they sum to `amount`.
- `allocations[].heads` splits the fee part across the invoice's heads **in proportion to what each
  head was charged**, with the rounding remainder on the last head so the shares always sum exactly.
  Proportional rather than one-head-at-a-time, so paying half a bill reads as half of everything
  rather than "Tuition paid, Transport untouched".
- `installmentName`, `dueDate` and `headName` are **copied onto the payment**, not resolved on read.
  A receipt reprinted in two years has to say what was paid for, not what the invoice says today.
- **Payments are append-only.** There is no update and no delete: a receipt has been handed over, so
  a mistake is corrected by cancelling the invoice or taking a further payment.
- `recordedBy` is the clerk's `uniqueId`, and null for a gateway capture that nobody recorded.

`GET /payments/mine` returns an **empty array**, not 404, for a login that is not a student's —
having paid nothing is a normal answer.

**`GET /payments/{id}`** is a deliberately small projection meant to be polled while a checkout
finishes. `receiptNo` and `receiptUrl` arrive with the money:

```json
{ "paymentId": "66f9...", "status": "CAPTURED", "receiptNo": "DVM-RCP-26-000001",
  "receiptUrl": "/api/v1/payments/66f9.../receipt.pdf" }
```

**Receipt PDF.** `GET /payments/{id}/receipt.pdf` answers `application/pdf` with
`Content-Disposition: inline` and a filename like `receipt-dvm-rcp-26-000001.pdf`, so it opens in an
`<iframe>` or a new tab and prints; there is no JSON variant. 422 for a payment that was never
captured — there is nothing to receipt until the money is in.

It is **rendered once, on the first request, and stored privately under `receipts/`**; every later
request serves the stored copy, so a reprint is byte-for-byte the document already handed over. It is
deliberately not rendered when the payment is taken: writing to the object store inside the payment
transaction would orphan a file on every rollback and would make the counter depend on the store
being up.

One A4 page: the logo, name and address from the configuration, the receipt number and date, the
student's name, unique ID, class and section, a table of the installments paid with their heads
under them, the late fine, the total, **the amount in words in the Indian system** ("Rupees One Lakh
Fifty Thousand Only" — lakh and crore, never "one hundred fifty thousand"), the mode and reference,
`Paid by: {payerName} ({relation})`, and `academicSettings.receiptFooter`. A missing logo or footer is
simply left out; the receipt is never refused over branding.

#### Collection report
`GET /fees/reports/collection?from=&to=` — **both dates inclusive**, resolved in the school's own
timezone (`app.timezone`), so "today's collection" means the school's day and not UTC's. Defaults to
the current month to date. The range may not exceed 400 days, and `to` before `from` is 400.

```json
{ "from": "2026-04-01", "to": "2026-04-30",
  "totalCollected": 4092000, "principalCollected": 4050000, "lateFineCollected": 42000,
  "paymentCount": 3,
  "byMode": [ { "mode": "CHEQUE", "amount": 2728000, "paymentCount": 2 },
              { "mode": "CASH", "amount": 1364000, "paymentCount": 1 } ],
  "byHead": [ { "headId": "66f1...", "headName": "Tuition", "amount": 3240000 },
              { "headId": "66f2...", "headName": "Transport", "amount": 810000 } ],
  "daily": [ { "date": "2026-04-20", "amount": 1364000, "paymentCount": 1 } ] }
```

- Only `CAPTURED` payments count; a created-but-unpaid gateway order would otherwise inflate every
  total.
- `totalCollected` = `principalCollected + lateFineCollected`. `byMode` and `daily` each sum to
  `totalCollected`; **`byHead` sums to `principalCollected` only**, because a late fine belongs to no
  head — which is exactly why it is reported separately.
- The one exception: an online payment with an `unallocatedAmount` is money received that no invoice
  needed, so it is in `totalCollected`, `byMode` and `daily` but in neither `principalCollected` nor
  `byHead`. That is the honest reading — the school holds it and has no bill for it — and it is why
  the field exists rather than being rounded away.
- `byMode` and `byHead` are ordered by amount, highest first. `daily` is oldest first and **omits
  days with no payments** rather than padding them with zeros.
- Every figure is a sum of values stored on the payments when they were taken, never re-derived from
  invoices — so last month's report still says the same thing after somebody corrects a structure.

Heads, structures, concessions, generation runs, every recalculation, every payment and every
receipt first rendered are written to `audit_logs`.

### Payments (online)
What the gateway flow adds on top of the [Payments](#payments) section above. The `Payment` shape, the
receipt PDF, `/payments/mine` and the collection report are **unchanged** — an online payment becomes
a `Payment` with `mode: ONLINE`, is allocated by the same rules and is receipted by the same code.

| Method | Path | Who |
|---|---|---|
| POST | `/payments/orders` | `FEE_PAY_SELF` (STUDENT). `{invoiceIds[], payerName, payerRelation}` |
| POST | `/payments/verify` | `FEE_PAY_SELF` (STUDENT). `{razorpayOrderId, razorpayPaymentId, razorpaySignature}` |
| GET | `/payments/{id}` | ADMIN, or the student themselves. Poll this while the checkout finishes |
| POST | `/public/payments/webhook` | Razorpay only. Signature-verified |

Only ADMIN and the student are ever in this flow. There are no parent accounts, so a parent paying
uses the student's login and gives their own name as `payerName` — that is what the receipt prints.

#### 1. Create the order
`POST /payments/orders` with `{ "invoiceIds": ["66f3..."], "payerName": "Rajesh Sharma",
"payerRelation": "FATHER" }` → 201:

```json
{ "paymentId": "66f9...", "orderId": "order_PqR8sT...", "amount": 1364000, "currency": "INR",
  "keyId": "rzp_test_xxxxxxxx", "name": "Delhi Valley Model School",
  "prefill": { "name": "Rajesh Sharma", "email": "rajesh@example.com", "contact": "9876543210" } }
```

- **There is no `amount` in the request, by design.** The server computes it from the invoices —
  outstanding fee plus the late fine due *today* — and creates the gateway order for exactly that
  figure, which the checkout will not let the client change (§ rule: never trust client amounts).
- The invoices must **belong to the caller** and still be `UNPAID` or `PARTIAL`; otherwise 400 with
  per-id `errors`. 422 if there is nothing left to pay on them.
- `keyId` is the **publishable** key. The key secret and the webhook secret never appear in any
  response.
- `name` is the school's own name from configuration, for the checkout heading. `prefill` fields may
  each be null, in which case the checkout simply asks.
- A `Payment` now exists with `status: CREATED`, `mode: ONLINE` and `gatewayOrderId`. **No money has
  moved**, so it has no `receiptNo`, no allocations, and is in no collection report.
- 503 `FEATURE_DISABLED` if this deployment has no gateway credentials configured.

#### 2. Verify what the checkout returned
`POST /payments/verify` with the three values Razorpay Checkout hands the browser → 200 with the same
shape as `GET /payments/{id}`:

```json
{ "paymentId": "66f9...", "status": "CAPTURED", "receiptNo": "DVM-RCP-26-000001",
  "receiptUrl": "/api/v1/payments/66f9.../receipt.pdf" }
```

- The signature is checked **first** — HMAC-SHA256 of `orderId|paymentId` under the key secret,
  compared in constant time — so an unverified request causes no gateway traffic. 403 if it does not
  verify, and **nothing is credited**.
- The payment's real status is then **read back from the gateway**, never inferred from the request: a
  signature proves the checkout happened, not that the bank settled. `authorized` is captured and then
  settled, `captured` is settled, `failed` becomes `FAILED`. Anything unresolved is left alone and the
  client keeps polling.
- **This endpoint is for speed, not truth.** The webhook settles the same payment through the same
  code, so a family that closes the tab loses nothing.

#### 3. Settling
One shared, **idempotent** step, whichever of the three paths reaches it. In a single transaction it
marks the payment `CAPTURED`, stores `gatewayPaymentId`, allocates the money across the invoices by
**exactly the rules the counter uses** — oldest due date first, late fine before fee — and assigns the
`receiptNo`. The receipt PDF is rendered immediately after that transaction commits, so it is there
when the family's page finishes polling; writing to the object store *inside* the transaction would
orphan a file on every rollback.

- `status` moves `CREATED` → `CAPTURED` **only on a verified gateway response.** Nothing a client
  sends can move it.
- Settled at most once. The status is re-read inside the transaction, and a **unique index on
  `gatewayPaymentId`** means one payment at the gateway can never be settled against two rows. A
  redelivered webhook, or verify racing the webhook, returns the receipt that already exists.
- The amount is **re-quoted from the invoices at settlement**, not taken from the order: minutes have
  passed and the late fine may have grown by a day. Whatever the invoices can absorb is allocated.
- `unallocatedAmount` appears only in the narrow case where they can absorb less than the gateway took
  — a clerk took the same invoices at the counter in between, or a concession was applied. The payment
  is still `CAPTURED` (the money is in), and this is the part owed back. It is audited as
  `PAYMENT_UNALLOCATED` precisely because somebody has to decide on a refund.
- A failure gives `status: FAILED` and a `failureReason` in the gateway's words, safe to show.

#### 4. The webhook
`POST /public/payments/webhook` is unauthenticated, like everything under `/public/**` — the gateway
has no login. **The signature authenticates it**: HMAC-SHA256 of the *raw* body under
`RAZORPAY_WEBHOOK_SECRET`, in `X-Razorpay-Signature`, verified before anything is parsed or written.

- `payment.captured` settles; `payment.failed` marks it `FAILED`. **Every other event is ignored.**
- Answers **200** for anything handled or deliberately ignored — unknown event, an order that is not
  ours, a payment already settled — because the gateway redelivers on non-2xx and a 200 is how
  "nothing to do" is said without starting a retry loop. 403 for a bad signature; 5xx only for a
  genuine failure, which *should* be retried.
- A deployment with no `RAZORPAY_WEBHOOK_SECRET` set **refuses every call** rather than trusting them.

#### 5. Reconciliation
A scheduled job runs **every 15 minutes** over online payments left `CREATED` for more than 15
minutes, asks the gateway what was ever paid against each order, and settles it or marks it `FAILED`.
This is the net under the other two paths: a tab closed before verify ran and a webhook lost during a
restart both end here. An attempt the gateway still calls `created` is left for the next run — failing
a payment that is about to succeed is how a capture ends up with nowhere to go.

Order creation, every settlement, every failure and every unallocated remainder are written to
`audit_logs`. **No secret or signature is ever logged**, and a refusal says that the signature did not
verify, never what was compared.

### Payroll
| Method | Path | Who |
|---|---|---|
| GET | `/payroll/structures/{employeeUniqueId}` | `PAYROLL_MANAGE` (ADMIN). Current version plus full history |
| PUT | `/payroll/structures/{employeeUniqueId}` | `PAYROLL_MANAGE`. **Creates a new version**; never overwrites |
| POST | `/payroll/advances` | `PAYROLL_MANAGE`. Recovered from future runs |
| GET | `/payroll/advances?employeeUniqueId=` | `PAYROLL_MANAGE`. One employee's, or everything still owed |
| POST | `/payroll/runs` | `PAYROLL_MANAGE`. `{month, prorateByAttendance}` → PENDING records; idempotent |
| GET | `/payroll/runs/{month}` | `PAYROLL_MANAGE`. Records plus totals |
| POST | `/payroll/records/{id}/pay` | `PAYROLL_MANAGE`. `{mode, reference, paidOn}` |
| POST | `/payroll/records/pay-bulk` | `PAYROLL_MANAGE`. `{recordIds, mode, paidOn}` |
| DELETE | `/payroll/records/{id}` | `PAYROLL_MANAGE`. PENDING only, so a month can be re-run |
| GET | `/payroll/mine` | `PAYROLL_READ_SELF` (TEACHER, STAFF). Records plus totals `{paid, unpaid}` |
| GET | `/payroll/records/{id}/slip.pdf` | Any authenticated caller; ADMIN or **that employee** only |

**All money is `long` paise**, as everywhere else. A month is always the string `yyyy-MM` — in the
body, in the path and on the record — because it sorts chronologically as text and brings no timezone
with it.

#### Salary structure
`GET /payroll/structures/{employeeUniqueId}`:

```json
{ "employeeUniqueId": "DEMO-EMP-26-0007", "employeeName": "Asha Menon",
  "current": {
    "id": "67a1...", "version": 2, "basic": 4000000,
    "allowances": [ { "name": "HRA", "amount": 1600000 },
                    { "name": "Conveyance", "amount": 200000 } ],
    "deductions": [ { "name": "PF", "type": "PERCENT_OF_BASIC", "value": 1200, "amount": 480000 },
                    { "name": "Professional tax", "type": "FIXED", "value": 20000, "amount": 20000 } ],
    "allowanceTotal": 1800000, "deductionTotal": 500000, "gross": 5800000,
    "effectiveFrom": "2026-07-01", "createdBy": "DEMO-EMP-26-0001",
    "createdAt": "2026-06-28T06:15:00Z" },
  "history": [ { "version": 2, "...": "..." }, { "version": 1, "...": "..." } ] }
```

- **Nothing is ever overwritten.** `PUT` inserts the next `version` and leaves its predecessors
  exactly as they were, because a salary slip printed last year has to stay reproducible and "what
  was this person on in April" is a question schools get asked years later.
- Send the **whole structure** on a PUT. Allowances and deductions you leave out are gone from the new
  version — that is how one is removed.
- `current` is the version in force **today**, and is `null` when every version there is starts in the
  future (a raise saved in advance). `history` is every version, newest first, future-dated ones
  included. An employee with no structure yet is **not a 404**: `current` is null, `history` is empty.
- A deduction is a **rule, not an amount**: `type` is `FIXED` (`value` is paise) or
  `PERCENT_OF_BASIC` (`value` is **basis points** — `1200` is 12%, `75` is 0.75%). Percentages are not
  whole numbers in practice and money never passes through a `double`. `amount` is the rule costed
  against that version's basic, so a client never has to know about basis points to show a figure.
- Percentage deductions are costed against the **full basic**, never a prorated one: PF and
  professional tax are what they are regardless of how many days somebody turned up.
- 422 if a name repeats in either list, if a percentage exceeds 100%, or if **the deductions come to
  more than the gross** — an over-deducting structure does not fail, it quietly pays somebody zero
  every month until a human notices.
- 409 if two admins save a version at the same moment: a unique index on (employee, version) decides
  it and the loser reloads.

#### Advances
`POST /payroll/advances` with `{employeeUniqueId, amount, givenOn, monthlyRecovery, reason}` → 201:

```json
{ "id": "67b4...", "employeeUniqueId": "DEMO-EMP-26-0007", "employeeName": "Asha Menon",
  "amount": 3000000, "givenOn": "2026-08-10", "monthlyRecovery": 1000000,
  "recoveredSoFar": 1000000, "remaining": 2000000, "status": "ACTIVE",
  "reason": "Medical" }
```

- Recovered automatically by every run, `monthlyRecovery` at a time, **oldest advance first** when
  there is more than one. The last installment is whatever is left, not the full monthly figure.
- `recoveredSoFar` moves when a salary is actually **paid**, not when a run plans it. A PENDING record
  is only a plan, so deleting one and re-running the month costs nobody an installment. A run still
  looks at what other pending records have already planned against an advance, or two unpaid months
  would both deduct the same final installment.
- `status` closes to `CLOSED` by itself, on the payment that recovers the last of it. There is no
  update and no delete: recovery is the only thing that moves.
- `givenOn` defaults to today and may not be in the future. 422 if `monthlyRecovery` exceeds the
  advance itself.
- `GET /payroll/advances` without a query parameter returns every **ACTIVE** advance in the school —
  the list the office keeps open. With `employeeUniqueId` it returns that employee's, closed ones
  included.

#### The run
`POST /payroll/runs` with `{"month": "2026-10", "prorateByAttendance": true}` → 201:

```json
{ "month": "2026-10", "prorateByAttendance": true,
  "created": 24, "skipped": 1,
  "missingStructure": [ "Vikram Rao (DEMO-EMP-26-0031)" ] }
```

One PENDING record per **ACTIVE** employee — teachers and staff alike, login or no login — who has a
structure effective for the month.

- **Idempotent.** Employees who already have a record for the month come back under `skipped` and are
  left untouched, guaranteed by a unique index on `(month, employeeUniqueId)`. Fixing a structure and
  running again is the normal way to finish a run.
- `missingStructure` names the active employees nothing could be computed for, as
  `"Name (UNIQUE-ID)"`. **Nothing was written for them**, and a rerun picks them up.
- The run is **not one transaction**, deliberately: each employee is written on their own, so one
  duplicate cannot abort everybody else, and a half-finished run is completed by running it again.
- "Effective for the month" means the latest version whose `effectiveFrom` falls on or before the
  **last day** of the month: a raise agreed from the 15th is what that person was on when the month
  ended, and a school payslip does not split a month across two structures.
- 422 for a month in the future — there is no attendance to prorate by and no month to pay for.

**Proration.** With `prorateByAttendance: true`, the employee register (§ Attendance) is read for the
month and pay is docked:

```
perDay    = gross / calendar days in the month
lossOfPay = perDay × ABSENT days  +  half a perDay per HALF_DAY        (capped at gross)
net       = gross − deductions − lossOfPay − advance recovery          (floored at zero)
```

- **LEAVE, holidays, non-working days and days nobody marked are paid.** An unmarked day is a gap in
  the record, not evidence of absence — docking pay for it would make a forgotten register come out of
  somebody's salary. PRESENT and LATE are a full day's work.
- The divisor is **calendar** days, so the per-day rate is the month's own and a 28-day February is
  not worth more per day than it should be.
- With `prorateByAttendance: false`, `lossOfPay` is 0 by decision and the register is not read at all.
  `prorated` on the record says which it was.
- Advance recovery is capped at what is left after deductions and loss of pay, so a record never plans
  to recover money that was never withheld from anybody.

#### The month's register
`GET /payroll/runs/{month}`:

```json
{ "month": "2026-10",
  "records": [ {
    "id": "67c8...", "month": "2026-10",
    "employeeUniqueId": "DEMO-EMP-26-0007", "employeeName": "Asha Menon",
    "employeeType": "TEACHER", "designation": null, "structureVersion": 2,
    "basic": 4000000,
    "allowances": [ { "name": "HRA", "amount": 1600000 },
                    { "name": "Conveyance", "amount": 200000 } ],
    "gross": 5800000,
    "deductions": [ { "name": "PF", "type": "PERCENT_OF_BASIC", "value": 1200, "amount": 480000 },
                    { "name": "Professional tax", "type": "FIXED", "value": 20000, "amount": 20000 } ],
    "deductionTotal": 500000, "advanceRecovery": 1000000,
    "prorated": true, "daysInMonth": 31, "absentDays": 2, "halfDays": 1,
    "lossOfPay": 467740, "net": 3832260,
    "status": "PENDING", "paidOn": null, "mode": null, "reference": null,
    "slipUrl": "/api/v1/payroll/records/67c8.../slip.pdf" } ],
  "totals": { "gross": 5800000, "net": 3832260, "paid": 0, "pending": 3832260 } }
```

- A record is a **snapshot, not a view**. The employee's name and designation, the structure version,
  every allowance and every deduction as costed are copied in at run time and never recomputed, so a
  raise in November does not change October's figures or October's slip.
- `designation` is the staff job title and is null for a teacher; the slip falls back to the type.
- Records are ordered by employee name. Totals are in paise, and `paid + pending == net` always —
  `paid` is the net of the PAID records and `pending` the net of the rest. `gross` is before anything
  was withheld.
- A month nobody has run comes back with an empty `records` array and zero totals, **not 404**:
  "nothing computed yet" is the normal state of next month, and it is the same screen you run it from.

#### Paying out
`POST /payroll/records/{id}/pay` with `{"mode": "BANK_TRANSFER", "reference": "UTR12345",
"paidOn": "2026-11-01"}` → the record, now `PAID`.

- `mode` is `BANK_TRANSFER` | `CASH` | `CHEQUE` | `UPI`. `reference` is the UTR, cheque number or UPI
  reference, and is optional because cash has none.
- `paidOn` defaults to today and may be backdated but **never postdated** — a salary is recorded when
  it goes out.
- Paying is what **moves the advance balance**: each planned installment is added to that advance's
  `recoveredSoFar`, capped at what is actually still owed, and the advance closes when the last of it
  comes back.
- 409 if the record has already been paid. Paying twice would recover the advance twice.

`POST /payroll/records/pay-bulk` with `{recordIds, mode, paidOn}` is the same thing for one bank run,
without a per-record reference — pay records one at a time when each needs its own UTR:

```json
{ "paid": 23, "alreadyPaid": [ "67c9..." ], "notFound": [], "netPaid": 88141980 }
```

- Forgiving by design: an id already paid, or gone because the month was re-run, is reported back
  rather than failing the batch — one stale id should not stop fifty people being paid. Replaying the
  same list pays nothing twice.

`DELETE /payroll/records/{id}` → 204, and only while `PENDING`. This is how a month is re-run after a
structure is corrected. 409 for a record already paid: a paid salary is a fact, and correcting one is
a decision for the office rather than a DELETE. The discarded record is kept in full in the audit
trail.

#### `GET /payroll/mine`
The caller's own records, newest month first, in the same record shape as above:

```json
{ "records": [ { "...": "..." } ], "totals": { "paid": 7664520, "unpaid": 3832260 } }
```

Empty rather than 404 for somebody who joined this month. There is no way to read anybody else's
records here — the uniqueId comes from the token, never from the request.

#### Salary slip PDF
`GET /payroll/records/{id}/slip.pdf` answers `application/pdf` with `Content-Disposition: inline` and
a filename like `salary-slip-demo-emp-26-0007-2026-10.pdf`, so it opens in an `<iframe>` or a new tab
and prints; there is no JSON variant. **ADMIN, or the employee the record belongs to** — anybody else
gets 403, including a teacher asking for a colleague's.

Unlike a fee receipt, it is **rendered on every request and not stored**: a slip is derived entirely
from a record that is itself frozen, so rendering it twice gives the same page and a stored copy would
only be another thing to keep in step. A `PENDING` record has a slip too, marked PENDING on its face —
the office prints it to show somebody what they are about to be paid.

One A4 page: the logo, name and address from the configuration, the month, the employee's name,
unique ID and designation (or their type, for a teacher), a table of earnings down to the gross, a
table of deductions including the advance recovery and the loss of pay with the absence it came from
("3 absent, 1 half day of 31 days"), the net pay in figures and **in words in the Indian system**
("Rupees Thirty Eight Thousand Three Hundred Twenty Two and Sixty Paise Only" — lakh and crore,
never "one hundred fifty thousand"), the paid status, date and mode, and
`academicSettings.salarySlipFooter`. A missing logo or footer is simply left out; the slip is never
refused over branding.

Every version of a structure, every advance and its recovery, every run, every record created, paid
or discarded is written to `audit_logs`: `SALARY_STRUCTURE_VERSIONED`, `SALARY_ADVANCE_CREATED`,
`SALARY_ADVANCE_RECOVERED`, `PAYROLL_RUN`, `PAYROLL_RECORD_CREATED`, `PAYROLL_RECORD_PAID` and
`PAYROLL_RECORD_DELETED`. A run's entry holds the counts, since each record it wrote has its own.

### Dashboards
| Method | Path | Who |
|---|---|---|
| GET | `/dashboard/admin` | ADMIN. Counts, today's attendance %, fee collected this month vs due, pending salaries, recent payments |
| GET | `/dashboard/teacher` | TEACHER |
| GET | `/dashboard/student` | STUDENT |

### Audit trail
| Method | Path | Who |
|---|---|---|
| GET | `/audit?entityType=&entityId=&action=&from=&to=&page=&size=` | `AUDIT_READ` (ADMIN) |

All filters are optional. `from` is inclusive, `to` is exclusive, both ISO-8601 UTC. `action` must be
one of the known action values or the response is 400 `BAD_REQUEST`. Default sort is `at` descending;
paged as in §1.

```json
{
  "items": [{
    "id": "64f0c0ffee00000000000001",
    "action": "SCHOOL_CONFIG_UPDATED",
    "entityType": "SchoolConfig",
    "entityId": "school-config",
    "actor": { "id": null, "uniqueId": "DEMO-EMP-26-0001", "role": "ADMIN" },
    "at": "2026-04-01T09:30:00Z",
    "before": { "identity": { "name": "Old Name" } },
    "after": { "identity": { "name": "New Name" } },
    "detail": null
  }],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1,
  "first": true, "last": true, "empty": false
}
```

- `actor.uniqueId` is `SYSTEM` for startup and background work, `ANONYMOUS` for unauthenticated
  requests such as a failed login.
- `before` and `after` are null for actions with no state (a login) and for one side of a
  creation or deletion. `detail` is an optional note.
- Any field that looks like credential material — `password`, `passwordHash`, `token`, `secret`,
  `apiKey` and similar, at any depth — is stored and returned as `"[redacted]"`. No audit entry ever
  contains a password, hash or token.
- **Retention: audit entries are kept indefinitely and are never deleted or expired**, by this API or
  by a TTL. Actions audited so far: `SCHOOL_CONFIG_SEEDED`, `SCHOOL_CONFIG_UPDATED`, and the
  authentication actions listed in §3's companion set (`LOGIN_SUCCESS`, `LOGIN_FAILURE`,
  `ACCOUNT_LOCKED`, `PASSWORD_CHANGED`, `LOGOUT`, `REFRESH_TOKEN_REUSE_REVOKED`) once B2 ships.

### AI (Phase 5, behind feature flag `app.features.ai`)
| Method | Path | Who |
|---|---|---|
| POST | `/ai/quiz-draft` | ADMIN, TEACHER. `{subjectId, topic, count, difficulty}` → draft questions (not saved) |
| POST | `/ai/report-remarks` | ADMIN, TEACHER. `{examId, studentUniqueId}` → remark text |
| POST | `/ai/insights` | ADMIN. Returns at-risk students with reasons |
| POST | `/public/ai/chat` | anyone. Landing-page FAQ bot; rate-limited |
