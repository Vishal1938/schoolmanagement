# STATUS REPORT — School Management Backend

Generated 2026-09-29. Branch `main`, last commit `a929422 B0: project skeleton for the school management backend`.
Everything below B0 is **uncommitted working-tree state** (see `git status`): B1 and B3 exist only as
untracked/modified files.

No code was modified to produce this report.

---

## ⚠️ Read this first — live credentials are committed

`src/main/resources/application.yml:8`, `src/main/resources/application-local.yml:16` and
`.env.example:11` all contain a **real, working MongoDB Atlas connection string with a plaintext
password** as the hard-coded default:

```
mongodb+srv://singhvishal35038_db_user:<REDACTED-16-CHAR-PASSWORD>@cluster0.izbsyjz.mongodb.net/school?...
```

- It is not just in the git-ignored `.env`; it is in three tracked files, two of which are already
  committed in `a929422` and are further modified in the working tree. It is in git history.
- `.env.example` is explicitly the template that is meant to be committed and shared.
- I verified the credential is live: the app connected to the Atlas replica set
  `atlas-661xwa-shard-0` during the startup run in §8.

Additionally, the git-ignored `.env` contains a real `JWT_SECRET`, a real `ENCRYPTION_KEY`, and the
Atlas password a second time as `MONGODB_PASSWORD=`. That file is correctly git-ignored.

**Recommended (not done — you said do not change code): rotate the Atlas password, then replace the
defaults in `application.yml` / `application-local.yml` / `.env.example` with placeholders.**

All secrets are masked in this report.

---

## 1. Project tree of `src/`

```
src/
├── main/
│   ├── java/com/school/
│   │   ├── SchoolManagementApplication.java
│   │   ├── academics/          {api,app,domain,infra}/package-info.java  + package-info.java
│   │   ├── ai/                 {api,app,domain,infra}/package-info.java  + package-info.java
│   │   ├── attendance/         {api,app,domain,infra}/package-info.java  + package-info.java
│   │   ├── auth/               {api,app,domain,infra}/package-info.java  + package-info.java
│   │   ├── common/
│   │   │   ├── package-info.java
│   │   │   ├── audit/
│   │   │   │   ├── AuditAction.java
│   │   │   │   ├── AuditActor.java
│   │   │   │   ├── AuditActorResolver.java
│   │   │   │   ├── AuditController.java
│   │   │   │   ├── AuditLog.java
│   │   │   │   ├── AuditLogRepository.java
│   │   │   │   ├── AuditLogResponse.java
│   │   │   │   ├── AuditSanitizer.java
│   │   │   │   ├── AuditSearch.java
│   │   │   │   ├── AuditService.java
│   │   │   │   └── package-info.java
│   │   │   ├── config/
│   │   │   │   ├── AppProperties.java
│   │   │   │   ├── ClockConfig.java
│   │   │   │   ├── MongoClientConfig.java
│   │   │   │   ├── MongoTransactionConfig.java
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   ├── WebConfig.java
│   │   │   │   └── package-info.java
│   │   │   ├── exceptions/
│   │   │   │   ├── AppException.java
│   │   │   │   ├── BusinessRuleException.java
│   │   │   │   ├── ConflictException.java
│   │   │   │   ├── ErrorType.java
│   │   │   │   ├── FieldViolation.java
│   │   │   │   ├── ForbiddenException.java
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── NotFoundException.java
│   │   │   │   ├── ProblemDetailFactory.java
│   │   │   │   ├── ValidationException.java
│   │   │   │   └── package-info.java
│   │   │   ├── id/
│   │   │   │   ├── Counter.java
│   │   │   │   ├── IdGenerator.java
│   │   │   │   ├── IdType.java
│   │   │   │   └── package-info.java
│   │   │   ├── jwt/            package-info.java          ← EMPTY
│   │   │   ├── pagination/
│   │   │   │   ├── PageResponse.java
│   │   │   │   └── package-info.java
│   │   │   ├── pdf/            package-info.java          ← EMPTY
│   │   │   ├── permissions/    package-info.java          ← EMPTY
│   │   │   ├── security/
│   │   │   │   ├── ProblemDetailAccessDeniedHandler.java
│   │   │   │   ├── ProblemDetailAuthenticationEntryPoint.java
│   │   │   │   ├── ProblemDetailResponseWriter.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── package-info.java
│   │   │   └── storage/
│   │   │       ├── ImageCategory.java
│   │   │       ├── ImageType.java
│   │   │       ├── ImageUploadService.java
│   │   │       ├── StorageConfig.java
│   │   │       ├── StoredImage.java
│   │   │       └── package-info.java
│   │   ├── dashboard/          {api,app,domain,infra}/package-info.java  + package-info.java
│   │   ├── exams/              {api,app,domain,infra}/package-info.java  + package-info.java
│   │   ├── fees/               {api,app,domain,infra}/package-info.java  + package-info.java
│   │   ├── notice/             {api,app,domain,infra}/package-info.java  + package-info.java
│   │   ├── payment/            {api,app,domain,infra}/package-info.java  + package-info.java
│   │   ├── payroll/            {api,app,domain,infra}/package-info.java  + package-info.java
│   │   ├── people/             {api,app,domain,infra}/package-info.java  + package-info.java
│   │   ├── quiz/               {api,app,domain,infra}/package-info.java  + package-info.java
│   │   └── schoolconfig/
│   │       ├── package-info.java
│   │       ├── api/
│   │       │   ├── ImageUploadResponse.java
│   │       │   ├── PublicSchoolController.java
│   │       │   ├── PublicSchoolResponse.java
│   │       │   ├── SchoolConfigController.java
│   │       │   ├── SchoolConfigMapper.java
│   │       │   ├── SchoolConfigRequest.java
│   │       │   ├── SchoolConfigResponse.java
│   │       │   └── package-info.java
│   │       ├── app/
│   │       │   ├── SchoolConfigSeeder.java
│   │       │   ├── SchoolConfigService.java
│   │       │   └── package-info.java
│   │       ├── domain/
│   │       │   ├── AcademicLevel.java
│   │       │   ├── AcademicSettings.java
│   │       │   ├── Academics.java
│   │       │   ├── ContactDetails.java
│   │       │   ├── GradeBand.java
│   │       │   ├── GradingMode.java
│   │       │   ├── GradingScheme.java
│   │       │   ├── Highlight.java
│   │       │   ├── Identity.java
│   │       │   ├── Landing.java
│   │       │   ├── Principal.java
│   │       │   ├── SchoolConfig.java
│   │       │   ├── SocialLinks.java
│   │       │   ├── Stats.java
│   │       │   ├── Theme.java
│   │       │   └── package-info.java
│   │       └── infra/
│   │           ├── SchoolConfigRepository.java
│   │           └── package-info.java
│   └── resources/
│       ├── application.yml
│       ├── application-local.yml
│       └── seed/school-seed.json
└── test/java/com/school/
    ├── ModularityTests.java
    ├── MongoReplicaSetIT.java
    ├── common/
    │   ├── ApplicationStartupTest.java
    │   ├── audit/{AuditControllerTest, AuditLogIT, AuditSanitizerTest, AuditServiceTest}.java
    │   ├── exceptions/GlobalExceptionHandlerTest.java
    │   ├── id/{IdGeneratorConcurrencyIT, IdGeneratorTest}.java
    │   └── storage/ImageUploadServiceTest.java
    └── schoolconfig/
        ├── SchoolConfigFixtures.java
        ├── SchoolConfigSeedingIT.java
        ├── api/{PublicSchoolControllerTest, SchoolConfigControllerTest}.java
        └── app/{SchoolConfigSeederTest, SchoolConfigServiceTest}.java
```

**11 of the 13 feature packages are completely empty** — only `package-info.java` files. Only
`common` and `schoolconfig` contain code. The `auth` package is empty, as are `common/jwt`,
`common/pdf` and `common/permissions`.

---

## 2. Full file contents

### `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<!--
		  Latest stable Spring Boot 3.x. Pinned to 3.x (not 4.x) because Spring AI 1.0.x,
		  which task B18 uses, supports Spring Boot 3.4/3.5 only.
		-->
		<version>3.5.6</version>
		<relativePath/>
	</parent>

	<groupId>com.school</groupId>
	<artifactId>schoolmanagement</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>schoolmanagement</name>
	<description>School management backend (one deployment per school)</description>

	<properties>
		<java.version>21</java.version>
		<!-- Spring Modulith 1.4.x is the line built against Spring Boot 3.5. -->
		<spring-modulith.version>1.4.1</spring-modulith.version>
		<springdoc.version>2.8.9</springdoc.version>
		<jjwt.version>0.12.6</jjwt.version>
		<mapstruct.version>1.6.3</mapstruct.version>
		<lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
		<razorpay.version>1.4.8</razorpay.version>
		<openpdf.version>1.3.30</openpdf.version>
		<poi.version>5.2.5</poi.version>
		<awssdk.version>2.31.30</awssdk.version>
	</properties>

	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.modulith</groupId>
				<artifactId>spring-modulith-bom</artifactId>
				<version>${spring-modulith.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
			<dependency>
				<groupId>software.amazon.awssdk</groupId>
				<artifactId>bom</artifactId>
				<version>${awssdk.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>

	<dependencies>
		<!-- Web / persistence / validation / ops -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-mongodb</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-aop</artifactId>
		</dependency>

		<!-- Module boundaries (verified by a test) -->
		<dependency>
			<groupId>org.springframework.modulith</groupId>
			<artifactId>spring-modulith-starter-core</artifactId>
		</dependency>

		<!-- API docs -->
		<dependency>
			<groupId>org.springdoc</groupId>
			<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
			<version>${springdoc.version}</version>
		</dependency>

		<!-- JWT -->
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-api</artifactId>
			<version>${jjwt.version}</version>
		</dependency>
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-impl</artifactId>
			<version>${jjwt.version}</version>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-jackson</artifactId>
			<version>${jjwt.version}</version>
			<scope>runtime</scope>
		</dependency>

		<!-- DTO mapping / boilerplate -->
		<dependency>
			<groupId>org.mapstruct</groupId>
			<artifactId>mapstruct</artifactId>
			<version>${mapstruct.version}</version>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<scope>provided</scope>
		</dependency>

		<!-- Payments -->
		<dependency>
			<groupId>com.razorpay</groupId>
			<artifactId>razorpay-java</artifactId>
			<version>${razorpay.version}</version>
		</dependency>

		<!-- PDF (report cards, receipts, salary slips) -->
		<dependency>
			<groupId>com.github.librepdf</groupId>
			<artifactId>openpdf</artifactId>
			<version>${openpdf.version}</version>
		</dependency>

		<!-- Excel import/export -->
		<dependency>
			<groupId>org.apache.poi</groupId>
			<artifactId>poi-ooxml</artifactId>
			<version>${poi.version}</version>
		</dependency>

		<!-- Object storage (MinIO locally, any S3-compatible store in production) -->
		<dependency>
			<groupId>software.amazon.awssdk</groupId>
			<artifactId>s3</artifactId>
		</dependency>
		<dependency>
			<groupId>software.amazon.awssdk</groupId>
			<artifactId>s3-transfer-manager</artifactId>
		</dependency>

		<!-- Test -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.security</groupId>
			<artifactId>spring-security-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.modulith</groupId>
			<artifactId>spring-modulith-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-testcontainers</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>junit-jupiter</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>mongodb</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<configuration>
					<annotationProcessorPaths>
						<path>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
							<version>${lombok.version}</version>
						</path>
						<path>
							<groupId>org.mapstruct</groupId>
							<artifactId>mapstruct-processor</artifactId>
							<version>${mapstruct.version}</version>
						</path>
						<path>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok-mapstruct-binding</artifactId>
							<version>${lombok-mapstruct-binding.version}</version>
						</path>
					</annotationProcessorPaths>
					<compilerArgs>
						<arg>-parameters</arg>
						<!--
						  MapStruct's -Amapstruct.* options are set on the mappers themselves
						  (@Mapper(componentModel = "spring", unmappedTargetPolicy = ERROR)) rather than
						  here, so the build stays warning-free while no mapper exists yet.
						-->
					</compilerArgs>
				</configuration>
			</plugin>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
				<configuration>
					<excludes>
						<exclude>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
						</exclude>
					</excludes>
				</configuration>
			</plugin>
			<!-- *IT tests (Testcontainers) run in the verify phase -->
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-failsafe-plugin</artifactId>
				<executions>
					<execution>
						<goals>
							<goal>integration-test</goal>
							<goal>verify</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>

</project>
```

### `src/main/resources/application.yml`

Line 8 masked.

```yaml
spring:
  application:
    name: schoolmanagement
  main:
    banner-mode: off
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb+srv://singhvishal35038_db_user:***MASKED***@cluster0.izbsyjz.mongodb.net/school?retryWrites=true&w=majority&appName=Cluster0}
      # Indexes are declared in code with @Indexed/@CompoundIndex, so let Spring Data create them.
      auto-index-creation: true
      uuid-representation: standard
    web:
      pageable:
        # API_CONTRACT.md §1: page is 0-based and size is at most 100. Enforced here rather than in
        # every controller, so no list endpoint can be talked into returning the whole collection.
        default-page-size: 20
        max-page-size: 100
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false
  servlet:
    multipart:
      # Exam papers are capped at 20 MB by the vault itself (B14); this is the hard transport limit.
      max-file-size: 25MB
      max-request-size: 30MB
  threads:
    virtual:
      enabled: true

server:
  port: ${SERVER_PORT:8080}
  error:
    whitelabel:
      enabled: false
  shutdown: graceful
  # Hardened further in B19.
  max-http-request-header-size: 16KB
  tomcat:
    max-swallow-size: 30MB

app:
  api-base-path: /api/v1
  school-code: ${SCHOOL_CODE:DEMO}
  timezone: ${APP_TIMEZONE:Asia/Kolkata}
  features:
    ai: ${APP_FEATURES_AI:false}
  jwt:
    # Must be overridden per deployment; startup fails fast in B2 if it is left at the dev value.
    secret: ${JWT_SECRET:change-me-in-every-deployment-this-is-a-dev-only-secret}
    access-ttl: ${JWT_ACCESS_TTL:15m}
    refresh-ttl: ${JWT_REFRESH_TTL:7d}
    cookie-path: /api/v1/auth
  storage:
    endpoint: ${STORAGE_ENDPOINT:http://localhost:9000}
    bucket: ${STORAGE_BUCKET:school-media}
    access-key: ${STORAGE_ACCESS_KEY:minioadmin}
    secret-key: ${STORAGE_SECRET_KEY:minioadmin}
    region: ${STORAGE_REGION:us-east-1}
    public-prefix: public/
    path-style-access: true
    # Where public objects are read from. Empty means endpoint/bucket, which is what MinIO serves
    # locally; in production set it to the R2 custom domain or public bucket URL.
    public-base-url: ${STORAGE_PUBLIC_BASE_URL:}
    max-image-size: ${STORAGE_MAX_IMAGE_SIZE:5MB}
  razorpay:
    key-id: ${RAZORPAY_KEY_ID:}
    key-secret: ${RAZORPAY_KEY_SECRET:}
    webhook-secret: ${RAZORPAY_WEBHOOK_SECRET:}
  bootstrap-admin:
    email: ${BOOTSTRAP_ADMIN_EMAIL:}
    password: ${BOOTSTRAP_ADMIN_PASSWORD:}
  encryption:
    # Base64 AES-256 key for employee bank details (B6).
    key: ${ENCRYPTION_KEY:}
  ai:
    api-key: ${AI_API_KEY:}
  seed:
    # First boot loads school_config from this file. Any Spring resource location works; point it at
    # file:./seed/school-seed.json to change a deployment's seed without rebuilding.
    enabled: ${APP_SEED_ENABLED:true}
    school-config-location: ${APP_SEED_SCHOOL_CONFIG:classpath:seed/school-seed.json}

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
  health:
    mongo:
      enabled: true

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: alpha
    tags-sorter: alpha

logging:
  level:
    root: INFO
    com.school: INFO
```

Note: `app.ai.api-key` is set here but `AppProperties` has **no `ai` component** — see §3.

### `src/main/resources/application-local.yml`

Line 16 masked.

```yaml
# Local development profile: ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
#
# Reads the untracked .env in the project root (copy it from .env.example). The file is parsed as
# properties, so KEY=value lines land as properties named exactly like the environment variables
# referenced from application.yml.
#
# The import is deliberately NOT `optional:`. The path is relative to the working directory, so a
# launch from anywhere but the project root would silently skip it, fall back to the localhost
# MONGODB_URI default in application.yml and only surface 30 s later as a Mongo server-selection
# timeout. Failing at startup instead names the real problem.
spring:
  config:
    import: file:./.env[.properties]
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb+srv://singhvishal35038_db_user:***MASKED***@cluster0.izbsyjz.mongodb.net/school?retryWrites=true&w=majority&appName=Cluster0}

logging:
  level:
    com.school: DEBUG
    org.springframework.data.mongodb.core.MongoTemplate: DEBUG

management:
  endpoint:
    health:
      show-details: always

springdoc:
  swagger-ui:
    try-it-out-enabled: true
```

The comment says the fallback is "the localhost `MONGODB_URI` default in application.yml". That is no
longer true — the fallback in both files is the Atlas cluster, so a launch from the wrong directory
would connect to Atlas rather than fail. The comment describes an earlier version of the file.

### `docker-compose.yml`

```yaml
# Local dependencies only: the backend itself runs from the IDE or ./mvnw spring-boot:run.
# Production compose (backend + Nginx + TLS + backups) is written in B19.
name: school-local

services:
  # MongoDB as a single-node replica set. A replica set is mandatory, not optional: payment and
  # receipt writes use multi-document transactions, which standalone mongod does not support.
  mongo:
    image: mongo:7
    container_name: school-mongo
    command: ["mongod", "--replSet", "rs0", "--bind_ip_all"]
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--quiet", "--eval", "db.runCommand({ ping: 1 }).ok"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 10s

  # One-shot: initiates rs0 if it is not initiated yet, then exits. Safe to re-run.
  mongo-init:
    image: mongo:7
    container_name: school-mongo-init
    depends_on:
      mongo:
        condition: service_healthy
    volumes:
      - ./docker/mongo:/scripts:ro
    # The member host is localhost:27017 so clients on the host machine can reach the advertised
    # member; containers on this network should connect with directConnection=true.
    command: ["mongosh", "--host", "mongo", "--quiet", "--file", "/scripts/init-replica-set.js"]
    restart: "no"

  # MinIO, our local stand-in for the S3-compatible store used in production.
  #
  # NOTE ON THE IMAGE: minio/minio and minio/mc are no longer publicly pullable (Docker Hub returns
  # "object not found", quay.io returns 401), so this uses Bitnami's archived MinIO build, which is
  # the same MinIO server. If you have access to an official MinIO registry or a mirror, swap the two
  # image references below; nothing else changes, because the app only speaks the S3 API to it.
  minio:
    image: bitnamilegacy/minio:latest
    container_name: school-minio
    environment:
      MINIO_ROOT_USER: ${STORAGE_ACCESS_KEY:-minioadmin}
      MINIO_ROOT_PASSWORD: ${STORAGE_SECRET_KEY:-minioadmin}
      MINIO_DEFAULT_BUCKETS: ${STORAGE_BUCKET:-school-media}
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/bitnami/minio/data

  # One-shot: creates the bucket if it is missing and makes only the public/ prefix anonymously
  # readable (logo, favicon, gallery). Everything else — receipts, exam papers, credential
  # sheets — stays private and is served through pre-signed URLs.
  minio-init:
    image: bitnamilegacy/minio-client:latest
    container_name: school-minio-init
    depends_on:
      - minio
    environment:
      BUCKET: ${STORAGE_BUCKET:-school-media}
      ACCESS_KEY: ${STORAGE_ACCESS_KEY:-minioadmin}
      SECRET_KEY: ${STORAGE_SECRET_KEY:-minioadmin}
    entrypoint:
      - /bin/sh
      - -c
      - |
        set -e
        until mc alias set local http://minio:9000 "$$ACCESS_KEY" "$$SECRET_KEY" >/dev/null 2>&1; do
          echo "waiting for minio..."
          sleep 2
        done
        mc mb --ignore-existing "local/$$BUCKET"
        mc anonymous set download "local/$$BUCKET/public"
        echo "bucket $$BUCKET ready (only public/ is anonymously readable)"
    restart: "no"

volumes:
  mongo-data:
  minio-data:
```

Two notes: the compose file defines a local Mongo replica set that **nothing currently uses** (both
yml files point at Atlas), and it pins `bitnamilegacy/minio:latest` / `bitnamilegacy/minio-client:latest`,
archived images, with the reason documented inline.

### `.env.example`

Line 11 masked. **This file is committed and contains a real credential.**

```bash
# Copy to .env for local development: cp .env.example .env
# .env is git-ignored and must never contain production secrets.
# In production these are real environment variables, not a file.

# --- identity -----------------------------------------------------------------
SCHOOL_CODE=DEMO
APP_TIMEZONE=Asia/Kolkata

# --- database (transactions require a replica set) ----------------------------
# Local docker compose: single-node replica set.
MONGODB_URI=mongodb+srv://singhvishal35038_db_user:***MASKED***@cluster0.izbsyjz.mongodb.net/school?retryWrites=true&w=majority&appName=Cluster0
# MongoDB Atlas: already a replica set, so drop replicaSet/directConnection. Keep the
# /<database> path segment — without it Spring Data has no default database. URL-encode
# any of : / ? # [ ] @ in the password.
# MONGODB_URI=mongodb+srv://<user>:<password>@<cluster>.mongodb.net/school?retryWrites=true&w=majority

# --- auth ---------------------------------------------------------------------
# Generate with: openssl rand -base64 48
JWT_SECRET=local-dev-secret-please-replace-with-at-least-32-bytes
JWT_ACCESS_TTL=15m
JWT_REFRESH_TTL=7d

# Used only on first boot to create the initial ADMIN (gets an EMP uniqueId).
BOOTSTRAP_ADMIN_EMAIL=admin@demo-school.local
BOOTSTRAP_ADMIN_PASSWORD=ChangeMe!234

# Base64 AES-256 key for employee bank details. Generate with: openssl rand -base64 32
ENCRYPTION_KEY=

# --- object storage (MinIO locally, any S3-compatible store in production) -----
STORAGE_ENDPOINT=http://localhost:9000
STORAGE_BUCKET=school-media
STORAGE_ACCESS_KEY=minioadmin
STORAGE_SECRET_KEY=minioadmin
STORAGE_REGION=us-east-1
# Where public images (logo, favicon, gallery) are read from. Leave empty locally: MinIO serves them
# at STORAGE_ENDPOINT/STORAGE_BUCKET. In production set the R2 custom domain or public bucket URL,
# e.g. https://media.your-school.example
STORAGE_PUBLIC_BASE_URL=
STORAGE_MAX_IMAGE_SIZE=5MB

# --- first-boot seeding -------------------------------------------------------
# school_config is seeded from this file when the database has none. Override the location to use a
# real seed without rebuilding, e.g. file:./seed/school-seed.json
APP_SEED_ENABLED=true
APP_SEED_SCHOOL_CONFIG=classpath:seed/school-seed.json

# --- payments -----------------------------------------------------------------
RAZORPAY_KEY_ID=
RAZORPAY_KEY_SECRET=
RAZORPAY_WEBHOOK_SECRET=

# --- optional features --------------------------------------------------------
APP_FEATURES_AI=false
AI_API_KEY=

# SMTP is optional; notifications stay disabled unless it is configured.
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=
```

Also note: the comment above the `MONGODB_URI` line says "Local docker compose: single-node replica
set", but the value is an Atlas URI. The comment and the value disagree.

---

## 3. Classes by package

Status key: **IMPLEMENTED** = complete for its stated scope; **PARTIAL** = works but something named
is missing; **STUB** = placeholder only.

### `com.school` (root)

| Class | Purpose | Status |
|---|---|---|
| `SchoolManagementApplication` | Boot entry point; `@Modulithic`, `@EnableConfigurationProperties(AppProperties)`, `@EnableScheduling`. | IMPLEMENTED |

### `com.school.common.audit`

| Class | Purpose | Status |
|---|---|---|
| `AuditAction` | Closed enum of audited actions (6 auth actions + 2 school-config actions). | **PARTIAL** — the 6 auth actions are declared but nothing emits them; B2 does not exist. |
| `AuditActor` | Record `{id, uniqueId, role}` with `SYSTEM` / `ANONYMOUS` sentinels. | IMPLEMENTED |
| `AuditActorResolver` | Resolves the acting user from the `SecurityContext`. | **PARTIAL** — always returns `id=null, role=null` for authenticated users (`// B2: once the principal is an AuthPrincipal…`). Today only ever returns `SYSTEM` or `ANONYMOUS`. |
| `AuditController` | `GET /audit`, permission `AUDIT_READ`. | **PARTIAL** — endpoint exists but `AUDIT_READ` cannot be granted to anyone; unreachable. |
| `AuditLog` | The `audit_logs` document. `@CompoundIndex(entityType, entityId)` + `@Indexed` DESC on `at`. No TTL by design. | IMPLEMENTED |
| `AuditLogRepository` | `MongoRepository`, deliberately exposes no delete. | IMPLEMENTED |
| `AuditLogResponse` | Read DTO + `from(AuditLog)` factory. | IMPLEMENTED |
| `AuditSanitizer` | Recursively redacts sensitive field names (13 fragments) at any depth. | IMPLEMENTED |
| `AuditSearch` | Filter record `{entityType, entityId, action, from, to}`. | IMPLEMENTED |
| `AuditService` | `record(...)` overloads + paged `search`. Failures are not swallowed. | IMPLEMENTED |

### `com.school.common.config`

| Class | Purpose | Status |
|---|---|---|
| `AppProperties` | Typed `app.*` config: apiBasePath, schoolCode, timezone, features, jwt, storage, razorpay, bootstrapAdmin, encryption, seed. | **PARTIAL** — `app.ai.api-key` is set in `application.yml` but there is no `Ai` component on the record, so the value is unbound and unreadable. `jwt`, `razorpay`, `bootstrapAdmin` and `encryption` are declared but read by no code. |
| `ClockConfig` | `Clock` bean zoned to `app.timezone`. | IMPLEMENTED |
| `MongoClientConfig` | 5s server-selection/connect timeouts so an unreachable DB fails fast. | IMPLEMENTED |
| `MongoTransactionConfig` | `MongoTransactionManager` + `@EnableTransactionManagement`. | IMPLEMENTED |
| `OpenApiConfig` | OpenAPI info + global `bearerAuth` scheme. | **PARTIAL** — the comment says "No servers entry", but springdoc injects one automatically; the live spec has `"servers":[{"url":"http://localhost:8081","description":"Generated server url"}]`. Harmless (paths already carry `/api/v1`) but the comment's intent is not achieved. |
| `WebConfig` | Prefixes every `com.school` controller with `app.api-base-path`. | IMPLEMENTED |

### `com.school.common.exceptions`

| Class | Purpose | Status |
|---|---|---|
| `AppException` | Base deliberate exception carrying `ErrorType` + extra problem members. | IMPLEMENTED |
| `BusinessRuleException` | `UNPROCESSABLE` (422). | IMPLEMENTED |
| `ConflictException` | `CONFLICT` (409). | IMPLEMENTED |
| `ErrorType` | Closed enum of all 20 contract codes with status, title and `type` URI. | IMPLEMENTED |
| `FieldViolation` | `{field, message}` record for `errors[]`. | IMPLEMENTED |
| `ForbiddenException` | `FORBIDDEN` (403). | IMPLEMENTED |
| `GlobalExceptionHandler` | Single `@RestControllerAdvice`; validation, duplicate key, optimistic lock, upload size, catch-all with `errorId`; rethrows security exceptions. | IMPLEMENTED |
| `NotFoundException` | `NOT_FOUND` (404). | IMPLEMENTED |
| `ProblemDetailFactory` | Builds/enriches `ProblemDetail` with `code` + `timestamp`. | IMPLEMENTED |
| `ValidationException` | `VALIDATION_ERROR` carrying `errors[]`. | IMPLEMENTED |

### `com.school.common.id`

| Class | Purpose | Status |
|---|---|---|
| `Counter` | `counters` document; `_id` is the counter key (`STU-26`). | IMPLEMENTED |
| `IdGenerator` | Atomic `findAndModify`+`$inc`+upsert; `next(type[,year])`, `nextNumber(prefix,width[,year])`, `nextSequence(key)`. | IMPLEMENTED — but no caller exists yet; nothing in the app generates an ID. |
| `IdType` | `STU(5)`, `EMP(4)`. | IMPLEMENTED |

### `com.school.common.pagination`

| Class | Purpose | Status |
|---|---|---|
| `PageResponse<T>` | Contract-shaped paged response with three `of(...)` factories. | IMPLEMENTED |

### `com.school.common.security`

| Class | Purpose | Status |
|---|---|---|
| `SecurityConfig` | Stateless deny-by-default chain; BCrypt(12) encoder. | **PARTIAL** — no JWT filter, no `AuthenticationProvider`, no `UserDetailsService`, and `.cors(withDefaults())` has no `CorsConfigurationSource` bean (see §5). Every non-public endpoint is permanently 401. |
| `ProblemDetailAccessDeniedHandler` | 403 as `ProblemDetail`. | IMPLEMENTED |
| `ProblemDetailAuthenticationEntryPoint` | 401 as `ProblemDetail`. | IMPLEMENTED |
| `ProblemDetailResponseWriter` | Shared writer so filter-chain errors match controller errors. | IMPLEMENTED |

### `com.school.common.storage`

| Class | Purpose | Status |
|---|---|---|
| `ImageCategory` | `LOGO` / `FAVICON` / `GALLERY` → public folder name. | IMPLEMENTED |
| `ImageType` | PNG/JPEG/GIF/ICO/WebP magic-byte detection; SVG deliberately rejected. | IMPLEMENTED |
| `ImageUploadService` | Validates size + real format, PUTs under `public/`, returns the public URL. | IMPLEMENTED |
| `StorageConfig` | One `S3Client` bean, path-style, static credentials. | IMPLEMENTED |
| `StoredImage` | `{key, url, contentType, sizeBytes}`. | IMPLEMENTED |

### `com.school.common.jwt`, `common.pdf`, `common.permissions`

**STUB** — `package-info.java` only. `common/permissions` is where B2's `Permission` enum and
`RolePermissions` belong; `API_CONTRACT.md:77` already points the frontend at
`common/security/Permission.java`, which does not exist.

### `com.school.schoolconfig.api`

| Class | Purpose | Status |
|---|---|---|
| `ImageUploadResponse` | `{url}`. | IMPLEMENTED |
| `PublicSchoolController` | `GET /public/school`, 5-min public cache, opted out of bearer auth in the spec. | IMPLEMENTED |
| `PublicSchoolResponse` | Hand-written public allowlist + `FIELDS` set for leak tests. | IMPLEMENTED |
| `SchoolConfigController` | `GET`/`PUT /school/config`, `POST /school/config/images`. | **PARTIAL** — correct, but gated on `SCHOOL_CONFIG_MANAGE`, which nothing can grant. Unreachable. |
| `SchoolConfigMapper` | MapStruct, `unmappedTargetPolicy = ERROR`, explicit public-projection mappings. | IMPLEMENTED |
| `SchoolConfigRequest` | Full validated write shape, shared with the seed file. | IMPLEMENTED |
| `SchoolConfigResponse` | Full admin read shape + `code`/`updatedAt`. | IMPLEMENTED |

### `com.school.schoolconfig.app`

| Class | Purpose | Status |
|---|---|---|
| `SchoolConfigSeeder` | `ApplicationRunner`; first-boot seed from `app.seed.school-config-location`, strict JSON + Bean Validation, fails startup on a bad seed. | IMPLEMENTED |
| `SchoolConfigService` | Single gateway to the config; `get`, `isSeeded`, `update` (audited), `seedIfMissing` (audited), band overlap/gap/duplicate validation, and read models for B8/B9/B12/B13. | IMPLEMENTED |

### `com.school.schoolconfig.domain`

`AcademicLevel`, `AcademicSettings`, `Academics`, `ContactDetails`, `GradeBand`, `GradingMode`,
`GradingScheme`, `Highlight`, `Identity`, `Landing`, `Principal`, `SchoolConfig`, `SocialLinks`,
`Stats`, `Theme` — all **IMPLEMENTED**. `SchoolConfig` is the singleton document (`_id =
"school-config"`) with `@Version` optimistic locking.

### `com.school.schoolconfig.infra`

| Class | Purpose | Status |
|---|---|---|
| `SchoolConfigRepository` | `MongoRepository<SchoolConfig, String>`. | IMPLEMENTED |

### `academics`, `ai`, `attendance`, `auth`, `dashboard`, `exams`, `fees`, `notice`, `payment`, `payroll`, `people`, `quiz`

**STUB** — every one of these is five `package-info.java` files and nothing else. No documents, no
services, no controllers, no repositories.

---

## 4. REST endpoints that actually exist

Five. Confirmed against the controllers and against the live `/v3/api-docs`.

| Method | Full path | Required permission | Request DTO | Response DTO |
|---|---|---|---|---|
| GET | `/api/v1/public/school` | **none** (permitAll) | — | `PublicSchoolResponse` |
| GET | `/api/v1/school/config` | `SCHOOL_CONFIG_MANAGE` | — | `SchoolConfigResponse` |
| PUT | `/api/v1/school/config` | `SCHOOL_CONFIG_MANAGE` | `SchoolConfigRequest` (`@Valid`, JSON) | `SchoolConfigResponse` |
| POST | `/api/v1/school/config/images` | `SCHOOL_CONFIG_MANAGE` | `multipart/form-data`: `file` (`MultipartFile`), `category` (`ImageCategory` = `LOGO`\|`FAVICON`\|`GALLERY`) | `ImageUploadResponse` |
| GET | `/api/v1/audit` | `AUDIT_READ` | query: `entityType`, `entityId`, `action` (`AuditAction`), `from` (`Instant`), `to` (`Instant`), `Pageable` (default size 20, sort `at` DESC) | `PageResponse<AuditLogResponse>` |

Plus the framework endpoints: `/actuator/health`, `/actuator/health/**`, `/actuator/info`,
`/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`.

**Four of the five are dead.** `SCHOOL_CONFIG_MANAGE` and `AUDIT_READ` are strings in
`@PreAuthorize` expressions; no `Permission` enum, no `RolePermissions`, no authentication mechanism
exists to grant them. Only `GET /public/school` is callable.

`/actuator/info` is permitted in `SecurityConfig` and listed in `management.endpoints.web.exposure.include`,
but with no info contributors configured it has no content.

---

## 5. Security configuration

Source: `src/main/java/com/school/common/security/SecurityConfig.java`.

### Public paths (`permitAll`)

```
/actuator/health
/actuator/health/**
/actuator/info
/v3/api-docs
/v3/api-docs/**
/swagger-ui.html
/swagger-ui/**
/api/v1/public/**          (built from app.api-base-path + "/public/**")
OPTIONS /**
```

Everything else: `.anyRequest().authenticated()`. Verified live — `GET /api/v1/audit` → 401,
`POST /api/v1/auth/login` → 401.

### Other chain settings

- CSRF disabled (stateless, no cookie-authenticated mutations).
- `SessionCreationPolicy.STATELESS`.
- HTTP Basic, form login and logout all disabled. Anonymous enabled.
- Headers: `X-Frame-Options: DENY`, `X-XSS-Protection: 1; mode=block`,
  `Referrer-Policy: no-referrer`, HSTS `max-age=31536000; includeSubDomains`
  (HSTS is only emitted over HTTPS, so it does not appear in the plain-HTTP responses in §8 — correct).
- `PasswordEncoder` = `BCryptPasswordEncoder(12)`. **No code calls it.**
- `@EnableMethodSecurity` is on, so `@PreAuthorize` is active.
- 401 → `ProblemDetailAuthenticationEntryPoint`; 403 → `ProblemDetailAccessDeniedHandler`.

### CORS — **configured incorrectly**

`SecurityConfig.java:45` calls `.cors(Customizer.withDefaults())`, but **there is no
`CorsConfigurationSource` bean anywhere in the codebase** (grepped: the only `cors` match in
`src/main` is that one line) and there is no `spring.web.cors.*` / `management.endpoints.web.cors.*`
property in either yml.

Result: CORS is effectively off, and preflights are actively rejected. Verified live:

```
$ curl -i -X OPTIONS http://localhost:8081/api/v1/public/school \
       -H "Origin: http://evil.example" -H "Access-Control-Request-Method: GET"
HTTP/1.1 403
...
Invalid CORS request
```

Note this contradicts the `OPTIONS /** → permitAll` rule in the chain: Spring's `CorsFilter` runs
first and rejects the preflight before authorization is consulted. A browser on a different origin
(e.g. a Vite dev server on :5173) cannot call this API today. Per `API_CONTRACT.md:15` production
is same-origin behind Nginx so this is by design there, but local frontend development will fail.

### JWT filter order

**There is no JWT filter.** `common/jwt` contains only a `package-info.java`; `jjwt` is on the
classpath but unused; nothing calls `addFilterBefore`. The class comment at `SecurityConfig:20-21`
states this outright: *"Authentication itself (JWT filter, refresh cookies, the `mustChangePassword`
gate) is added in B2; until then there are no authenticated endpoints, so every non-public path
answers 401."*

Consequence: because no `UserDetailsService` bean exists, Spring Boot's
`UserDetailsServiceAutoConfiguration` kicks in and prints a generated password at startup:

```
Using generated security password: 95a14f0d-3c79-4743-a0f6-8b0ec3f29745
Global AuthenticationManager configured with UserDetailsService bean with name inMemoryUserDetailsManager
```

This is not currently exploitable — `httpBasic` and `formLogin` are both disabled, so there is no way
to present those credentials — but a random password is being written to the log on every boot, and
the in-memory `user` account will become live the moment any authentication entry point is added.

### Refresh-token cookie settings

**None exist.** No `ResponseCookie`, no `Set-Cookie`, no `refresh_tokens` collection, no TTL index.
The only trace is configuration that nothing reads:

- `app.jwt.cookie-path: /api/v1/auth` (`application.yml:53`)
- `AppProperties.Jwt.cookiePath` defaulting to `/api/v1/auth`
- `app.jwt.access-ttl: 15m`, `app.jwt.refresh-ttl: 7d`

The contract's required `httpOnly`, `Secure`, `SameSite=Strict` attributes are not set anywhere,
because nothing sets a cookie.

---

## 6. What startup actually seeds

| Thing | Status | Evidence |
|---|---|---|
| **School config** | ✅ Seeded | `SchoolConfigSeeder` (`ApplicationRunner`, `@ConditionalOnProperty(app.seed.enabled, matchIfMissing=true)`) reads `classpath:seed/school-seed.json`, parses it with `FAIL_ON_UNKNOWN_PROPERTIES`, runs Bean Validation, and inserts via `SchoolConfigService.seedIfMissing` at fixed `_id = "school-config"`. Writes an `SCHOOL_CONFIG_SEEDED` audit entry with actor `SYSTEM`. Confirmed live in §8: `GET /public/school` returns the seeded content. |
| **Bootstrap admin** | ❌ **Not seeded — no code exists** | `grep -rn "bootstrapAdmin\|BOOTSTRAP_ADMIN" src/main/java` returns exactly one hit: the `AppProperties` record component declaration. Nothing reads it. There is no `users` collection, no `User` document, no seeder. `BOOTSTRAP_ADMIN_EMAIL=admin@demo-school.local` and `BOOTSTRAP_ADMIN_PASSWORD=ChangeMe!234` in `.env` are inert. **No admin ID, no admin account, no login.** |
| **Dev users** | ❌ **Not seeded — no code exists** | There is no dev-user seeder of any kind in this repo. No user IDs, no passwords. |

The `seed/school-seed.json` content is a complete, realistic sample: "Demo Vidya Mandir Senior
Secondary School", 5 academic levels, 6 highlights, 12 facilities, 6 gallery URLs, 8 grade bands
(`BOTH` mode, covering 0–100 with no gap), 6 working days, 48-hour attendance edit window, receipt
prefix `DVM-RCP`, and both PDF footers.

The seed's `logoUrl` / `faviconUrl` / gallery URLs all point at `http://localhost:9000/school-media/...`
(local MinIO), so they are dead links in any non-local deployment.

---

## 7. `./mvnw verify`

**Result: BUILD SUCCESS** in 28.5 s. No compiler warnings.

### Unit tests (surefire)

```
[INFO] Results:
[INFO]
[INFO] Tests run: 91, Failures: 0, Errors: 0, Skipped: 0
```

| Test class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `com.school.ModularityTests` | 2 | 0 | 0 | 0 |
| `com.school.common.ApplicationStartupTest` | 4 | 0 | 0 | 0 |
| `com.school.common.audit.AuditControllerTest` | 7 | 0 | 0 | 0 |
| `com.school.common.audit.AuditSanitizerTest` | 6 | 0 | 0 | 0 |
| `com.school.common.audit.AuditServiceTest` | 8 | 0 | 0 | 0 |
| `com.school.common.exceptions.GlobalExceptionHandlerTest` | 6 | 0 | 0 | 0 |
| `com.school.common.id.IdGeneratorTest` | 11 | 0 | 0 | 0 |
| `com.school.common.storage.ImageUploadServiceTest` | 8 | 0 | 0 | 0 |
| `com.school.schoolconfig.api.PublicSchoolControllerTest` | 7 | 0 | 0 | 0 |
| `com.school.schoolconfig.api.SchoolConfigControllerTest` | 13 | 0 | 0 | 0 |
| `com.school.schoolconfig.app.SchoolConfigSeederTest` | 5 | 0 | 0 | 0 |
| `com.school.schoolconfig.app.SchoolConfigServiceTest` | 14 | 0 | 0 | 0 |
| **Total** | **91** | **0** | **0** | **0** |

### Integration tests (failsafe) — **all skipped**

```
[ERROR] org.testcontainers.dockerclient.DockerClientProviderStrategy --
        Could not find a valid Docker environment. Please check configuration.
        Attempted configurations were: ...

[WARNING] Tests run: 14, Failures: 0, Errors: 0, Skipped: 14
```

| IT class | Tests | Skipped |
|---|---:|---:|
| `com.school.common.audit.AuditLogIT` | 5 | 5 |
| `com.school.common.id.IdGeneratorConcurrencyIT` | 4 | 4 |
| `com.school.MongoReplicaSetIT` | 2 | 2 |
| `com.school.schoolconfig.SchoolConfigSeedingIT` | 3 | 3 |
| **Total** | **14** | **14** |

**`BUILD SUCCESS` here is misleading.** Testcontainers cannot reach a Docker daemon on this machine,
so every integration test is skipped rather than failed, and the build stays green. In particular
`IdGeneratorConcurrencyIT` — the 200-parallel-generation test that TASKS.md B3 names as its
acceptance criterion — **did not run**. Neither did the audit-log persistence, the seeding, nor the
replica-set checks. CI (`.github/workflows/ci.yml`, `ubuntu-latest`) does have a Docker daemon and
would run all 14, but I have no evidence of a CI result here.

---

## 8. Live responses on the `local` profile

### How this was run

Port 8080 was already occupied by a pre-existing instance of this app (PID 37132, started outside
this session, not on the `local` profile — its `/actuator/health` hides details). Rather than kill
the user's process, I started a second instance on port 8081:

```
./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=8081
```

Only the port differs; everything else is the `local` profile reading `.env`. It connected to the
Atlas replica set `atlas-661xwa-shard-0` (primary `ac-trsk5kk-shard-00-02`). Substitute `:8080` for
`:8081` mentally in the paths below.

### `GET /actuator/health`

```http
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Type: application/vnd.spring-boot.actuator.v3+json
Transfer-Encoding: chunked
Date: Tue, 29 Sep 2026 06:53:12 GMT

{"status":"UP","groups":["liveness","readiness"],"components":{"diskSpace":{"status":"UP","details":{"total":510704742400,"free":202565070848,"threshold":10485760,"path":"C:\\Projects\\schoolmanagement\\schoolmanagement\\.","exists":true}},"livenessState":{"status":"UP"},"mongo":{"status":"UP","details":{"maxWireVersion":25}},"ping":{"status":"UP"},"readinessState":{"status":"UP"},"ssl":{"status":"UP","details":{"validChains":[],"invalidChains":[]}}}}
```

### `GET /api/v1/public/school`

```http
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
Cache-Control: max-age=300, public
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Type: application/json
Transfer-Encoding: chunked
Date: Tue, 29 Sep 2026 06:53:12 GMT

{"name":"Demo Vidya Mandir Senior Secondary School","tagline":"Learn with curiosity, lead with character","logoUrl":"http://localhost:9000/school-media/public/logo/logo.png","faviconUrl":"http://localhost:9000/school-media/public/favicon/favicon.ico","theme":{"primary":"#0B3D91","secondary":"#F2A93B","accent":"#12B886"},"about":"Demo Vidya Mandir has taught the children of this district since 1994. We are a co-educational, English-medium school affiliated to the state board, teaching classes Nursery to XII across two shifts on a six-acre campus. Our class sizes are capped at thirty-five so that every child is known by name, and every teacher is expected to know how each of their students is doing, not just what the average is. Science and commerce streams are offered at the senior secondary level, alongside a humanities stream introduced in 2019.","vision":"To send out young people who think clearly, speak honestly and are useful to the community they live in.","facilities":["Physics, chemistry and biology laboratories","Computer laboratory with 40 workstations","Library with over 12,000 titles","Smart classrooms for classes VI to XII","Covered basketball and volleyball courts","400m athletics track and cricket ground","Music and dance studio","Art and craft room","Infirmary with a full-time nurse","GPS-tracked bus service on 14 routes","RO drinking water on every floor","CCTV-monitored corridors and gates"],"galleryImageUrls":["http://localhost:9000/school-media/public/gallery/campus-front.jpg","http://localhost:9000/school-media/public/gallery/assembly-ground.jpg","http://localhost:9000/school-media/public/gallery/science-lab.jpg","http://localhost:9000/school-media/public/gallery/library.jpg","http://localhost:9000/school-media/public/gallery/annual-day.jpg","http://localhost:9000/school-media/public/gallery/sports-meet.jpg"],"stats":{"students":1240,"teachers":68,"years":32,"passPercentage":98},"contact":{"addressLine1":"17 Shastri Marg","addressLine2":"Near Civil Lines Post Office","city":"Demo City","state":"Madhya Pradesh","postalCode":"462001","phone":"+91 755 400 1200","alternatePhone":"+91 98260 11223","email":"office@demo-vidya-mandir.example","websiteUrl":"https://demo-vidya-mandir.example"},"mapEmbedUrl":"https://www.google.com/maps/embed?pb=REPLACE_WITH_THE_EMBED_LINK_FROM_GOOGLE_MAPS","socialLinks":{"facebook":"https://facebook.com/demo-vidya-mandir","instagram":"https://instagram.com/demo_vidya_mandir","youtube":"https://youtube.com/@demo-vidya-mandir"}}
```

The public projection is correct: no `code`, no `gradingScheme`, no `academicSettings`, no
`receiptPrefix`. Null `socialLinks.twitter` / `.linkedin` are omitted, as are the whole `principal`,
`academics` and `highlights` objects — **wait, those three are missing from the response but are
present in the seed file.** See §9 for this discrepancy.

### `POST /api/v1/auth/login` (bootstrap admin)

```
$ curl -i -X POST http://localhost:8081/api/v1/auth/login \
       -H "Content-Type: application/json" \
       -d '{"uniqueId":"admin@demo-school.local","password":"ChangeMe!234"}'
```

```http
HTTP/1.1 401
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Type: application/problem+json;charset=UTF-8
Content-Length: 263
Date: Tue, 29 Sep 2026 06:53:12 GMT

{"type":"https://schoolmanagement.dev/problems/unauthorized","title":"Authentication required","status":401,"detail":"Authentication is required for this endpoint","instance":"/api/v1/auth/login","code":"UNAUTHORIZED","timestamp":"2026-09-29T06:53:12.725722800Z"}
```

**The endpoint does not exist.** This is not a credential rejection — there is no login controller,
so the path falls through to `anyRequest().authenticated()` and the entry point answers 401. There
was no `Set-Cookie` header and no token to carry forward.

### `GET /api/v1/auth/me`

No token could be obtained, so this was called without one:

```http
HTTP/1.1 401
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Type: application/problem+json;charset=UTF-8
Content-Length: 260
Date: Tue, 29 Sep 2026 06:53:12 GMT

{"type":"https://schoolmanagement.dev/problems/unauthorized","title":"Authentication required","status":401,"detail":"Authentication is required for this endpoint","instance":"/api/v1/auth/me","code":"UNAUTHORIZED","timestamp":"2026-09-29T06:53:12.805078400Z"}
```

The error shape matches `API_CONTRACT.md` §3 exactly on all seven required members.

### Bonus: live OpenAPI path list

```
GET  /api/v1/public/school
GET  /api/v1/school/config
PUT  /api/v1/school/config
POST /api/v1/school/config/images
GET  /api/v1/audit
```

---

## 9. Differences between the code and `API_CONTRACT.md`

### A. Missing endpoints — 60+ of the ~65 in §5 do not exist

Only `GET /public/school`, `GET/PUT /school/config`, `POST /school/config/images` and `GET /audit`
are implemented. Everything else in §5 is absent: all of Auth (§2), Academics, Students, Employees,
Member search, Attendance, Exams & marks, Exam papers, Quizzes, Notices, Fees, Payments, Payroll,
Dashboards and AI. `GET /public/notices?page=` is listed under "Public / school" in the contract and
is also absent (it belongs to B10).

This is expected for the current task position (B0–B3) and is a schedule fact, not a defect.

### B. `GET /public/school` omits `principal`, `academics` and `highlights` — needs investigation

The seed file `src/main/resources/seed/school-seed.json` contains a full `principal` object, a full
`academics` object with 5 levels, and 6 `highlights`. The live response in §8 contains **none of the
three**, while `facilities` and `galleryImageUrls` (sibling fields set in the same seed) are present.

The contract (`API_CONTRACT.md:211-216`) explicitly documents that these keys are omitted when the
school has not filled them in, so the *shape* is contract-legal — but the seeded school *has* filled
them in, so the data is being lost somewhere between the seed file and the response.

The most likely explanation is that the `school_config` document in the Atlas database was seeded by
an **earlier run against an earlier version of the seed file or the DTO**, and `seedIfMissing` is a
no-op once a document exists (`isSeeded()` short-circuits, by design). That would make this stale
data rather than a code bug. I did not modify anything to confirm, and I did not inspect the stored
document. **This needs to be checked before you trust the landing page** — either by querying
`school_config` directly, or by dropping it and letting the seeder re-run.

If the stored document does contain the three objects, then the bug is in `SchoolConfigMapper`'s
flattening or in `Landing`, and it is a real contract violation.

### C. CORS is broken for cross-origin development

`API_CONTRACT.md:15` says production is same-origin behind Nginx, "so no CORS is needed in
production". That is respected. But `SecurityConfig` calls `.cors(Customizer.withDefaults())`
without a `CorsConfigurationSource`, which does not mean "CORS off" — it means "CORS on, with no
allowed origins", and preflights get a hard `403 Invalid CORS request` (verified, §5). A frontend
dev server on another port cannot talk to this backend. The contract does not describe the local dev
story, so this is arguably an unstated gap rather than a contradiction — but it will block the
frontend repo on day one.

### D. `API_CONTRACT.md:77` points at a file that does not exist

> "The permission list lives in `backend/.../common/security/Permission.java`"

There is no `Permission.java`, in `common/security` or anywhere else. `common/permissions/` exists
but contains only a `package-info.java`. The contract is describing B2, which is not started.

### E. OpenAPI `servers` entry

`OpenApiConfig`'s comment says "No servers entry", and explains why one would be harmful. springdoc
adds one anyway; the live spec carries `"servers":[{"url":"http://localhost:8081", "description":
"Generated server url"}]`. Because that URL is host-only and the paths already include `/api/v1`,
the `/api/v1/api/v1/...` problem the comment warns about does not occur — but the stated intent is
not realised, and a generated client will bake in whatever host the spec was fetched from.

### F. Audit index name vs. contract wording

TASKS.md B3 asks for indexes on `(entityType, entityId)` and `timestamp`. The code has
`@CompoundIndex(entityType, entityId)` and `@Indexed` DESC on the field named `at`. The contract's
own JSON example (§ Audit trail) uses `at`, so the code matches the contract; only TASKS.md's wording
differs. Not a defect.

### G. Receipt-number format: TASKS.md and the contract disagree with each other

`API_CONTRACT.md:153` specifies `{receiptPrefix}-{YY}-{SEQ}` → `DVM-RCP-26-000001`.
`TASKS.md:158` (B12) specifies `{receiptPrefix}/{session}/{seq}`.

`IdGenerator.nextNumber(prefix, width)` produces the **contract** format. This will need resolving at
B12; flagging it now because the generator is already written to one of the two.

### H. Unverifiable contract clauses

These could not be checked because every endpoint that would exercise them is behind authentication
that does not exist yet:

- §1: `size` above 100 is capped, not rejected (the property `spring.data.web.pageable.max-page-size: 100`
  is set correctly, so this should hold, but no reachable list endpoint exists to prove it).
- § Audit trail: an unknown `action` value returns 400 `BAD_REQUEST`.
- §3: 404 `NOT_FOUND` for an unmatched route — currently an unmatched route under `/api/v1` returns
  401, not 404, because `anyRequest().authenticated()` fires before the dispatcher decides there is
  no handler. Once auth exists this will need rechecking; today it is arguably a contract deviation
  that is invisible.

---

## 10. Task status: B0, B1, B2, B3

### B0 — Project skeleton: **DONE** (with two caveats)

| Requirement | Evidence |
|---|---|
| Maven project, full stack from CLAUDE.md | `pom.xml`: Boot 3.5.6, Java 21, Web/Security/Mongo/Validation/Actuator/AOP, Modulith 1.4.1, springdoc 2.8.9, jjwt 0.12.6, MapStruct 1.6.3, Lombok, Razorpay 1.4.8, OpenPDF 1.3.30, POI 5.2.5, AWS SDK v2 S3. Boot version pinned to 3.x with the Spring AI / B18 rationale in a comment. ✅ |
| Full package layout with `package-info.java` | All 13 feature packages × 4 sub-packages present. ✅ |
| `docker-compose.yml`: Mongo 7 single-node RS + init script, MinIO + bucket auto-create | `docker-compose.yml` + `docker/mongo/init-replica-set.js` (idempotent, waits for primary election). ✅ |
| `application.yml` + `application-local.yml` reading `.env`, committed `.env.example` | All three present. ✅ |
| `PageResponse<T>` in contract shape | `common/pagination/PageResponse.java`, `items`/`totalItems` naming, 3 factories. ✅ |
| Global `@RestControllerAdvice` returning `ProblemDetail` with the contract's error types | `GlobalExceptionHandler` + `ErrorType` (all 20 codes) + `ProblemDetailFactory`. ✅ Verified live in §8. |
| `Clock` bean | `ClockConfig`, zoned to `app.timezone`. ✅ |
| springdoc configuration | `OpenApiConfig`; `/v3/api-docs` live. ✅ |
| Actuator health | Live, `UP`, Mongo component `UP`. ✅ |
| Spring Modulith `ApplicationModules.verify()` test | `src/test/java/com/school/ModularityTests.java`, 2 tests, passing. ✅ |
| GitHub Actions running `./mvnw verify` | `.github/workflows/ci.yml`, JDK 21 temurin, uploads test reports. ✅ |

**Caveat 1:** the "Done when" says *"the app starts locally against docker compose"*. It does not —
both yml files default to Atlas, and the run in §8 connected to Atlas. The compose stack is written
and correct but is not what the app actually uses. MinIO is likewise unexercised.

**Caveat 2:** the committed credential in §0 originates from this task's config files.

### B1 — School config and seeding: **DONE** (with one open question)

| Requirement | Evidence |
|---|---|
| `SchoolConfig` document: identity (name, code, tagline, logo, favicon, theme×3) | `domain/SchoolConfig.java`, `Identity`, `Theme`. ✅ |
| Landing content: about, vision, principal's message, facilities, gallery, stats, contact, map, social | `domain/Landing.java` + `Principal`, `Academics`, `Highlight`, `Stats`, `ContactDetails`, `SocialLinks`. ✅ (goes beyond the ask with `academics` and `highlights`) |
| Grading scheme `MARKS`/`GRADES`/`BOTH` with bands | `GradingMode`, `GradingScheme`, `GradeBand`, plus overlap/duplicate/coverage validation in `SchoolConfigService.validateGradingScheme`. ✅ |
| Academic settings: working days, attendance edit window, receipt prefix, receipt + salary footers | `domain/AcademicSettings.java`. ✅ |
| Startup seeding from `seed/school-seed.json` if missing | `SchoolConfigSeeder`. ✅ |
| Realistic sample seed | 171-line `school-seed.json`, fully populated. ✅ |
| `GET /public/school`, `GET /school/config`, `PUT /school/config` | All three exist. ✅ |
| Public endpoint returns only public-safe fields | `PublicSchoolResponse` is a hand-written allowlist with a `FIELDS` constant for leak tests; `PublicSchoolControllerTest` (7 tests) passing. ✅ Verified live. |
| `ImageUploadService` in `common/storage`, MinIO, `public/` prefix, public-read | `ImageUploadService` + `StorageConfig` + `ImageType` magic-byte detection. ✅ Bucket policy applied by `minio-init` in compose. |

**Open question:** §9(B) — the live `GET /public/school` is missing `principal`, `academics` and
`highlights` despite the seed containing them. Most likely a stale document in the Atlas database
rather than a code fault, but it is unresolved and it is the one thing standing between this task and
an unqualified DONE. Also untested end-to-end: the image upload path against a real MinIO (only unit
tests with a mocked `S3Client`, 8 tests).

### B2 — Auth, users, permissions: **NOT STARTED**

Nothing from this task exists.

| Requirement | Evidence of absence |
|---|---|
| `users` collection | No `User` document, no `users` collection, no repository. `grep -rln "UserDetailsService" src/main/java` → no matches. |
| `Permission` enum + `RolePermissions` | `common/permissions/` contains only `package-info.java`. `grep -rln "enum Permission\|RolePermissions"` → no matches. The 24 permission names in TASKS.md exist nowhere; the 2 used in `@PreAuthorize` (`SCHOOL_CONFIG_MANAGE`, `AUDIT_READ`) are bare strings. |
| JWT access token (HS256) | `common/jwt/` is empty. `jjwt` is a declared dependency with zero usages. |
| Opaque refresh token, hashed, `refresh_tokens` + TTL index, rotation, family revoke | `grep -rln "refresh_tokens\|ResponseCookie"` → no matches. |
| `httpOnly`/`Secure`/`SameSite=Strict` cookie on `/api/v1/auth` | No cookie is ever set. |
| Lockout after 5 failures for 15 min | No code. `AuditAction.ACCOUNT_LOCKED` and `ErrorType.LOCKED` are declared but unused. |
| `mustChangePassword` filter | No filter. `ErrorType.PASSWORD_CHANGE_REQUIRED` declared, unused. |
| Bootstrap admin from `BOOTSTRAP_ADMIN_*` on first boot | `AppProperties.BootstrapAdmin` is declared and read by nothing. |
| Login → refresh → logout tests | No auth tests. |

Live confirmation: `POST /api/v1/auth/login` → 401 `UNAUTHORIZED` (no such route), §8.

Side effect worth noting: because no `UserDetailsService` exists, Boot autoconfigures an in-memory
one and logs a generated password each boot (§5).

### B3 — Unique ID generator and audit log: **PARTIAL**

| Requirement | Status | Evidence |
|---|---|---|
| `IdGenerator` backed by `counters`, atomic `findAndModify` + `$inc` + upsert | ✅ DONE | `common/id/IdGenerator.java` — single `findAndModify`, no read-then-write anywhere. `Counter._id` *is* the key, so creation and increment are one operation. |
| Contract §4 format | ✅ DONE | `{SCHOOL_CODE}-{TYPE}-{YY}-{SEQ}`, `STU` width 5, `EMP` width 4, per-type-and-year counters. `nextNumber(prefix, width)` covers the receipt format. |
| **Concurrency test: 200 parallel generations, no duplicates** | ⚠️ **written but never executed** | `IdGeneratorConcurrencyIT` exists (4 tests) but **all 4 were skipped** in §7 — Testcontainers found no Docker daemon. `IdGeneratorTest` (11 tests, passing) is unit-level with a mocked `MongoOperations` and cannot prove atomicity. **TASKS.md's stated acceptance criterion for B3 has not actually been demonstrated on this machine.** |
| `AuditService.record(action, entityType, entityId, before, after)` | ✅ DONE | Exact signature present, plus two convenience overloads. Sanitizes both sides. |
| Actor from the security context | ⚠️ PARTIAL | `AuditActorResolver` works, but returns `id=null` and `role=null` for authenticated users — the code says `// B2: once the principal is an AuthPrincipal, take the user id and role from it as well`. Since B2 does not exist, every entry today is `SYSTEM` or `ANONYMOUS`. The contract's example actor `{"id":null,"uniqueId":"DEMO-EMP-26-0001","role":"ADMIN"}` is not yet producible. |
| Indexes on `(entityType, entityId)` and timestamp | ✅ DONE | `@CompoundIndex(name="audit_entity_idx")` and `@Indexed(name="audit_at_idx", DESCENDING)`. `auto-index-creation: true`. |
| `GET /audit?entityType=&entityId=` for ADMIN | ⚠️ PARTIAL | Endpoint exists with the full filter set and correct paging, but `@PreAuthorize("hasAuthority('AUDIT_READ')")` can never be satisfied — no authentication, no permission model. Verified live: 401. `AuditControllerTest` (7 tests) passes using mocked security. |
| **Done when:** concurrency test passes and audit entries appear | ❌ **Not demonstrated** | The concurrency test was skipped; `AuditLogIT` (5 tests, would prove entries appear, including the no-TTL assertion) was also skipped. The only real-world audit evidence is that `SCHOOL_CONFIG_SEEDED` should have been written during the first boot against Atlas — which I did not verify, because `GET /audit` is unreachable. |

**B3 verdict: the code is complete and looks correct, but both of its acceptance criteria are
unverified on this machine.** Run `./mvnw verify` somewhere with a working Docker daemon (or let CI
do it) before calling it done.

---

## Summary of things that are broken or missing

1. **Live Atlas credentials committed** in `application.yml`, `application-local.yml` and
   `.env.example`, all tracked, already in git history. Rotate the password.
2. **CORS rejects all preflights** — `.cors(withDefaults())` with no `CorsConfigurationSource`.
   Verified: `403 Invalid CORS request`.
3. **Four of the five endpoints are unreachable** — `SCHOOL_CONFIG_MANAGE` and `AUDIT_READ` cannot be
   granted to anyone.
4. **All 14 integration tests skip locally** (no Docker daemon), including B3's mandated
   200-parallel-ID concurrency test, so `BUILD SUCCESS` overstates what has been verified.
5. **`GET /public/school` is missing `principal`, `academics` and `highlights`** even though the seed
   file supplies them — probably a stale `school_config` document in Atlas, but unconfirmed.
6. **A random Spring Security password is logged on every boot** because no `UserDetailsService`
   exists; the in-memory `user` account becomes live as soon as any auth entry point is added.
7. **`app.ai.api-key` is unbindable** — set in `application.yml`, with no matching component on
   `AppProperties`.
8. **The app does not use its own docker-compose stack** — both profiles point at Atlas, so the
   Mongo replica set and MinIO defined in compose are never exercised. B0's "done when" is not
   literally met.
9. **`API_CONTRACT.md:77` references `common/security/Permission.java`**, which does not exist.
10. **Receipt-number format conflict** between `API_CONTRACT.md:153` and `TASKS.md:158`; the
    generator implements the contract's version.
11. Stale comments: `application-local.yml` describes a localhost Mongo fallback that is now Atlas;
    `.env.example` labels an Atlas URI as "Local docker compose"; `OpenApiConfig` claims there is no
    `servers` entry when springdoc adds one.
