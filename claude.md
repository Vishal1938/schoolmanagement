# CLAUDE.md — School Management Backend

This file gives you the context for every task in this repo. Read `TASKS.md` for the numbered task you have been asked to do, and treat `API_CONTRACT.md` as the binding contract with the frontend.

## Product in one paragraph
This is a school management system. Each school gets its **own separate deployment** of the backend, frontend and MongoDB; nothing is shared between schools. The code must stay school-agnostic: anything that differs per school goes in environment variables or in the `school_config` collection, which is seeded from `seed/school-seed.json` on first boot. There are four roles: ADMIN, TEACHER, STUDENT and STAFF. There are no parent accounts; parents use the student's login, and when paying they enter a "paid by" name that is printed on the receipt.

## Stack
- Java 21, Maven
- Spring Boot (latest stable 3.x). Before choosing the exact version, check that it is compatible with the Spring AI release used in task B18.
- Spring Web, Spring Security, Spring Data MongoDB, Bean Validation, Actuator
- Spring Modulith, to verify module boundaries in a test
- JWT via `jjwt`; passwords hashed with BCrypt (strength 12)
- MapStruct for DTO mapping, Lombok (for `@Getter`/`@Builder` only; use records for DTOs)
- springdoc-openapi (served at `/v3/api-docs` and `/swagger-ui.html`)
- Razorpay Java SDK; OpenPDF for PDFs; Apache POI for Excel
- AWS SDK v2 S3 client, pointed at MinIO locally and at any S3-compatible store in production
- Testing: JUnit 5, Testcontainers (MongoDB), MockMvc
- MongoDB 7+ running as a **replica set**, including a single-node replica set locally, because payment and receipt writes use transactions

## Package layout (by feature)
```
com.school
├── common        security, jwt, permissions, exceptions, audit, id generator, pagination, pdf, storage
├── schoolconfig
├── auth
├── academics     sessions, classes, sections, subjects, assignments
├── people        students, employees, member search, import
├── attendance
├── exams         exams, marks, report cards, exam paper vault
├── quiz
├── notice
├── fees          heads, structures, invoices, concessions, reports
├── payment       razorpay, webhook, receipts, reconciliation
├── payroll
├── dashboard
└── ai
```
Each feature package contains `api` (controllers and request/response DTOs), `app` (services), `domain` (documents and enums) and `infra` (repositories and clients).

## Module boundaries
- A module can only call another module through that module's public service in `app`. It must never use another module's repository directly.
- For side effects across modules, publish a Spring application event. For example, `PaymentCapturedEvent` is handled by `fees`.

## Non-negotiable rules
1. **Enforce authorization on the backend.** Use `@PreAuthorize` with permissions (e.g. `hasAuthority('FEE_READ_FULL')`), never role names. Object-level checks, such as "a student can only read their own records", belong in the service.
2. **Enforce field visibility with per-role DTO projections.** The teacher's view of a student must never contain fee amounts or payment history.
3. Store and return **money as `long` paise**. Never use `double` or `float`.
4. **Never trust amounts sent by the client.** Always compute payment amounts on the server from the invoices.
5. **Keep a full audit trail.** Every create, update and delete on marks, attendance, fees, payments, payroll and exam-paper access writes to `audit_logs` (who, what, when, before and after).
6. **Hardcode nothing school-specific.** School name, ID prefix, grading scheme, classes, fee heads, logo and colors come from config or the seed file.
7. **Never expose raw entities from controllers.** Always return DTOs.
8. **Handle all errors in one `@RestControllerAdvice`**, returning `ProblemDetail` in the shape described in `API_CONTRACT.md`.
9. **Never log secrets or passwords**, and never write full tokens to logs.
10. **Index what you query**, and declare the indexes in code (`@CompoundIndex`/`@Indexed`, or an index initializer).

## Configuration (env vars)
`SCHOOL_CODE`, `MONGODB_URI`, `JWT_SECRET`, `JWT_ACCESS_TTL=15m`, `JWT_REFRESH_TTL=7d`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `STORAGE_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`, `APP_FEATURES_AI=false`, `AI_API_KEY`, `SMTP_*` (optional)

## Definition of done (every task)
- The code compiles with no warnings you introduced.
- Unit tests cover the services, and at least one MockMvc test per endpoint covers happy path, forbidden and validation.
- The OpenAPI spec reflects the endpoints and they match `API_CONTRACT.md`. If they have to differ, update the contract and flag it in your summary so it can be copied to the frontend repo.
- The module-boundary test still passes.
- Finish with a short summary: what was built, anything not done, and any contract changes.

## Commands
- `docker compose up -d` starts MongoDB (replica set) and MinIO
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- `./mvnw verify`