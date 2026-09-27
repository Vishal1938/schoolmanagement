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
| Errors | RFC 7807 `ProblemDetail` (see §3) |
| Validation errors | HTTP 400, with `errors: [{field, message}]` |

### Pagination

Request: `?page=0&size=20&sort=name,asc` (page is 0-based; size is at most 100)

Response:
```json
{ "items": [], "page": 0, "size": 20, "totalItems": 134, "totalPages": 7 }
```

## 2. Auth

| Method | Path | Body / notes |
|---|---|---|
| POST | `/auth/login` | `{uniqueId, password}` → `{accessToken, expiresIn, user}`; sets the refresh token as an httpOnly cookie |
| POST | `/auth/refresh` | Uses the cookie → new `{accessToken, expiresIn}`; rotates the refresh token |
| POST | `/auth/logout` | Revokes the refresh token and clears the cookie |
| POST | `/auth/change-password` | `{currentPassword, newPassword}` |
| GET | `/auth/me` | Current `user` |

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

- Send the access token as `Authorization: Bearer <token>`. It expires after 15 minutes. The refresh token lasts 7 days.
- If `mustChangePassword` is true, every endpoint except `change-password`, `me` and `logout` returns 403 with `type = .../password-change-required`.
- There is no self-signup. Admins create all accounts, and the initial password is issued by the admin.

### Roles

`ADMIN`, `TEACHER`, `STUDENT`, `STAFF`

The frontend must decide what to show using **`permissions`**, not `role`. The permission list lives in `backend/.../common/security/Permission.java`, and each role maps to a fixed set of permissions.

## 3. Error shape

```json
{
  "type": "https://school.app/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "2 fields are invalid",
  "instance": "/api/v1/students",
  "errors": [{ "field": "dob", "message": "must be in the past" }]
}
```

Error types: `validation`, `not-found`, `forbidden`, `unauthorized`, `conflict`, `password-change-required`, `payment-failed`, `locked-resource`, `internal`.

## 4. Unique IDs

The format is `{SCHOOL_CODE}-{TYPE}-{YY}-{SEQ}`:
- `TYPE` is `STU` (5-digit sequence) or `EMP` (4-digit sequence). Teachers, staff and admins are all `EMP`.
- `YY` is the year of admission or joining.
- IDs are never reused. `SCHOOL_CODE` comes from the environment config.

## 5. Endpoint map

The `Who` column gives access. "Self" means the user's own records only.

### Public / school
| Method | Path | Who |
|---|---|---|
| GET | `/public/school` | anyone. Returns name, logo, tagline, theme colors, about, facilities, gallery, contact, social links, stats |
| GET | `/public/notices?page=` | anyone. Returns notices flagged `public=true` |
| GET | `/school/config` | ADMIN |
| PUT | `/school/config` | ADMIN |

### Academics
| Method | Path | Who |
|---|---|---|
| GET/POST | `/sessions` | GET: all authenticated; POST: ADMIN |
| PUT | `/sessions/{id}/activate` | ADMIN. Exactly one session is active |
| GET/POST/PUT | `/classes` (includes sections) | GET: all authenticated; write: ADMIN |
| GET/POST/PUT | `/subjects` | GET: all authenticated; write: ADMIN |
| PUT | `/classes/{id}/assignments` | ADMIN. Sets the class teacher and subject→teacher mapping |

### Students
| Method | Path | Who |
|---|---|---|
| GET | `/students?classId=&sectionId=&q=&feeStatus=&page=` | ADMIN, TEACHER |
| GET | `/students/{uniqueId}` | ADMIN (full), TEACHER (teacher view), STUDENT (self) |
| POST | `/students` | ADMIN. Also creates the login and returns the temporary password once |
| PUT | `/students/{uniqueId}` | ADMIN |
| PATCH | `/students/{uniqueId}/status` | ADMIN. `ACTIVE`, `LEFT`, `ALUMNI` |
| POST | `/students/import` (multipart .xlsx) | ADMIN. Returns `{created, failed: [{row, errors}]}` |
| GET | `/students/import/template` | ADMIN. Downloads the .xlsx template |
| POST | `/students/promote` | ADMIN. Bulk promotion to the next session/class |
| POST | `/students/{uniqueId}/reset-password` | ADMIN |

**Role-based projections.** These are enforced on the backend, not by hiding fields in the UI:
- `StudentAdminView`: every field.
- `StudentTeacherView`: basic profile, class, contact, attendance summary, marks, and `feeStatus` (`PAID` | `PARTIAL` | `DUE` | `OVERDUE`) only. It has **no amounts and no payment history**.
- `StudentSelfView`: own profile plus everything about themselves.

### Employees (teachers + staff)
| Method | Path | Who |
|---|---|---|
| GET | `/employees?type=TEACHER|STAFF&q=&page=` | ADMIN |
| GET | `/employees/{uniqueId}` | ADMIN, or self |
| POST / PUT | `/employees`, `/employees/{uniqueId}` | ADMIN |
| POST | `/employees/import` | ADMIN |
| POST | `/employees/{uniqueId}/reset-password` | ADMIN |

`employeeType` is `TEACHER` or `STAFF`. A staff member has a `designation` (e.g. Accountant, Driver, Peon, Librarian) and `hasLogin` (bool, default false).

### Member search
| Method | Path | Who |
|---|---|---|
| GET | `/members/{uniqueId}` | ADMIN, TEACHER. Resolves STU or EMP and returns `{memberType, data}` projected for the caller. A teacher searching an EMP id gets 403 unless it is their own |
| GET | `/members/search?q=` | ADMIN, TEACHER. Type-ahead by name or uniqueId, max 10 results |

### Attendance
| Method | Path | Who |
|---|---|---|
| GET | `/attendance/class/{classId}/{sectionId}?date=` | ADMIN, TEACHER |
| PUT | `/attendance/class/{classId}/{sectionId}?date=` | ADMIN, TEACHER. Body `{entries:[{studentUniqueId, status}]}` |
| GET | `/attendance/students/{uniqueId}?from=&to=` | ADMIN, TEACHER, self. Returns daily records plus a summary (%) |
| GET | `/attendance/employees?date=` / PUT | ADMIN |
| GET | `/attendance/employees/{uniqueId}?from=&to=` | ADMIN, self |

Student status values are `PRESENT`, `ABSENT`, `LATE`, `HALF_DAY`, `LEAVE`, `HOLIDAY`. Teachers can edit a day's attendance only on that day or the day after; admins can edit any day.

### Exams & marks
| Method | Path | Who |
|---|---|---|
| GET/POST/PUT | `/exams` | GET: all; write: ADMIN. Exam = name, session, classes, per-subject schedule and max marks |
| GET | `/exams/{examId}/marks?classId=&sectionId=&subjectId=` | ADMIN, TEACHER |
| PUT | `/exams/{examId}/marks` | ADMIN, TEACHER (only while the exam is not `PUBLISHED`) |
| POST | `/exams/{examId}/publish` | ADMIN. Makes results visible to students |
| GET | `/students/{uniqueId}/results?sessionId=` | ADMIN, TEACHER, self (published exams only) |
| GET | `/students/{uniqueId}/report-card/{examId}.pdf` | ADMIN, TEACHER, self |

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
| GET | `/notices?page=` | all authenticated. Filtered by audience |
| POST/PUT/DELETE | `/notices` | ADMIN (any audience), TEACHER (own classes only) |

Audience: `ALL`, `STUDENTS`, `TEACHERS`, `STAFF`, or `CLASS:{classId}[:{sectionId}]`. A notice can also be flagged `public` (shown on the landing page) and `pinned`, and can have attachments.

### Fees
| Method | Path | Who |
|---|---|---|
| GET/POST/PUT | `/fees/heads` | ADMIN (Tuition, Transport, Exam, Admission…) |
| GET/POST/PUT | `/fees/structures` | ADMIN. Per class per session: heads, amounts, installments, due dates, late fine rule |
| POST | `/fees/structures/{id}/generate-invoices` | ADMIN. Idempotent |
| POST | `/fees/concessions` | ADMIN. Per student: % or fixed amount, with reason |
| GET | `/fees/students/{uniqueId}` | ADMIN, self. Returns invoices, payments and totals `{totalDue, totalPaid, balance}` |
| GET | `/fees/students/{uniqueId}/status` | ADMIN, TEACHER, self. Returns `feeStatus` only |
| POST | `/fees/offline-payment` | ADMIN. Records cash, cheque or UPI paid at the counter |
| GET | `/fees/reports/collection?from=&to=` | ADMIN |
| GET | `/fees/reports/defaulters?classId=` | ADMIN |

### Payments (online)
| Method | Path | Who |
|---|---|---|
| POST | `/payments/orders` | STUDENT. `{invoiceIds[], payerName, payerRelation}` → `{orderId, amount, currency, keyId, prefill}` for Razorpay Checkout |
| POST | `/payments/verify` | STUDENT. `{razorpayOrderId, razorpayPaymentId, razorpaySignature}` → `{status}`. Used only for fast UX; the webhook is the source of truth |
| POST | `/public/payments/webhook` | Razorpay only. Signature-verified |
| GET | `/payments/{paymentId}/receipt.pdf` | ADMIN, self |
| GET | `/payments/mine` | STUDENT |

`payerRelation` is `SELF`, `FATHER`, `MOTHER`, `GUARDIAN` or `OTHER`.

### Payroll
| Method | Path | Who |
|---|---|---|
| GET/PUT | `/payroll/structures/{employeeUniqueId}` | ADMIN. Basic, allowances, deductions |
| POST | `/payroll/runs` | ADMIN. `{month:"2026-09"}` generates PENDING records; idempotent |
| GET | `/payroll/runs/{month}` | ADMIN |
| POST | `/payroll/records/{id}/pay` | ADMIN. `{mode, reference, paidOn}` |
| POST | `/payroll/advances` | ADMIN. Recovered from future runs |
| GET | `/payroll/mine` | TEACHER, STAFF with login. Returns records plus totals `{paid, unpaid}` |
| GET | `/payroll/records/{id}/slip.pdf` | ADMIN, self |

### Dashboards
| Method | Path | Who |
|---|---|---|
| GET | `/dashboard/admin` | ADMIN. Counts, today's attendance %, fee collected this month vs due, pending salaries, recent payments |
| GET | `/dashboard/teacher` | TEACHER |
| GET | `/dashboard/student` | STUDENT |

### AI (Phase 5, behind feature flag `app.features.ai`)
| Method | Path | Who |
|---|---|---|
| POST | `/ai/quiz-draft` | ADMIN, TEACHER. `{subjectId, topic, count, difficulty}` → draft questions (not saved) |
| POST | `/ai/report-remarks` | ADMIN, TEACHER. `{examId, studentUniqueId}` → remark text |
| POST | `/ai/insights` | ADMIN. Returns at-risk students with reasons |
| POST | `/public/ai/chat` | anyone. Landing-page FAQ bot; rate-limited |
