# Database Schemas

Per-service DDL. Each service owns its database; no cross-service
foreign keys.

## Conventions (apply everywhere)

- **Table names:** `snake_case`, plural (`users`, `resumes`, `jobs`)
- **Column names:** `snake_case`
- **Java fields:** `camelCase` — Hibernate's default naming strategy
  maps automatically (`firstName` → `first_name`)
- **Charset/collation:** `utf8mb4` / `utf8mb4_unicode_ci` on every table
- **Engine:** InnoDB
- **Primary keys:**
  - User-related: `email VARCHAR(255)` (the distributed user ID)
  - Other entities: `id VARCHAR(36)` (UUID, generated server-side)
- **Timestamps:** every table has `created_at DATETIME NOT NULL DEFAULT
  CURRENT_TIMESTAMP` and `updated_at DATETIME NOT NULL DEFAULT
  CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`
- **Soft delete:** `is_active BOOLEAN NOT NULL DEFAULT TRUE`.
  Repositories filter `is_active = TRUE` by default.
- **JSON columns:** MySQL 8 native `JSON` type. Used for
  skills/experience/education (see `CONVENTIONS.md` and the note in
  the *Querying skills* section below).
- **Cross-service FKs:** none. Email or ID is the only "link" and is
  validated at the application layer / via events.
- **Naming for foreign-ish columns:** the column points at, not the
  table it points from. `application.candidate_email` → references
  email in another service. `application.job_id` → references a job.

---

## Querying JSON skills (architectural note)

Skills, experience, and education are stored as JSON columns in
`userdb.users` and snapshotted as JSON columns in
`applicationdb.applications`. This means:

- **You cannot efficiently query "find candidates with skill X"
  directly against `userdb.users`.**
- Such queries are owned by **Matching Service** (`:8087`), which
  consumes profile-update events and builds its own indexed
  representation (likely a `skill → candidates` inverted index).
- Job Service does not query candidate skills. Application Service
  does not query candidate skills. Only Matching Service does.

This is intentional. Source-of-truth services stay simple; the
service that needs query performance owns the index it needs.

---

# auth_db (Auth Service)

## `users`
```sql
CREATE TABLE users (
    email          VARCHAR(255) NOT NULL,
    password       VARCHAR(255) NOT NULL,           -- BCrypt hash
    role           ENUM('JOB_SEEKER','RECRUITER','ADMIN') NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (email),
    INDEX idx_users_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Notes:**
- Already exists — this is documentation of current state.
- After Phase 0 (NFR-1.3), `password` is hashed by User Service and
  arrives via event already-hashed. Auth Service no longer hashes.
- After Phase 0 (NFR-1.6), service additionally verifies HMAC
  signature on requests; no schema change needed.

---

# userdb (User Service)

## `users`
```sql
CREATE TABLE users (
    email                VARCHAR(255) NOT NULL,
    role                 ENUM('JOB_SEEKER','RECRUITER','ADMIN') NOT NULL,

    -- Common profile fields
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) NOT NULL,
    phone_number         VARCHAR(20),
    date_of_birth        DATE,
    address              VARCHAR(1000),
    city                 VARCHAR(100),
    state                VARCHAR(100),
    country              VARCHAR(100),
    postal_code          VARCHAR(20),
    bio                  VARCHAR(2000),
    linkedin_url         VARCHAR(500),
    portfolio_url        VARCHAR(500),

    -- Candidate-only (NULL for recruiters)
    skills               JSON,        -- ["Java","Kafka","Spring"]
    experience           JSON,        -- [{ company, role, startDate, endDate, description }]
    education            JSON,        -- [{ institution, degree, field, startYear, endYear }]
    summary              VARCHAR(2000),
    years_of_experience  DECIMAL(4,1),
    job_preferences      JSON,        -- { locations:[], salaryMin, salaryMax, remote, employmentTypes:[] }

    -- Recruiter-only (NULL for candidates)
    company_name         VARCHAR(200),
    designation          VARCHAR(200),
    company_website      VARCHAR(500),

    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    is_email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (email),
    INDEX idx_users_role (role),
    INDEX idx_users_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**JSON shapes:**

```jsonc
// skills
["Java", "Kafka", "Spring", "MySQL"]

// experience
[
  {
    "company": "Acme",
    "role": "Backend Engineer",
    "startDate": "2022-01",
    "endDate": "2024-06",       // null = current
    "description": "Built event-driven payment service"
  }
]

// education
[
  {
    "institution": "IIT Bombay",
    "degree": "B.Tech",
    "field": "Computer Science",
    "startYear": 2018,
    "endYear": 2022
  }
]

// jobPreferences
{
  "locations": ["Bangalore", "Remote"],
  "salaryMin": 1500000,
  "salaryMax": 3000000,
  "currency": "INR",
  "remote": true,
  "employmentTypes": ["FULL_TIME", "CONTRACT"]
}
```

**Decisions baked in:**
- One `users` table for both candidates and recruiters. Role-specific
  fields are nullable. (FR-2.7 design choice — single table, faster to
  build, acceptable tradeoff.)
- `years_of_experience` as `DECIMAL(4,1)` — supports values like 2.5.
- `phone_number` as VARCHAR — never integer (leading zeros, country codes).

---

# resumedb (Resume Service)

## `resumes`
```sql
CREATE TABLE resumes (
    id              VARCHAR(36)  NOT NULL,
    user_email      VARCHAR(255) NOT NULL,
    label           VARCHAR(50)  NOT NULL,         -- "Resume 1", "Resume 2", or renamed
    file_name       VARCHAR(255) NOT NULL,         -- original filename
    object_key      VARCHAR(500) NOT NULL,         -- MinIO key (UUID-based, never user-supplied)
    file_size_bytes BIGINT       NOT NULL,
    content_type    VARCHAR(100) NOT NULL,         -- application/pdf, application/vnd.openxmlformats-...
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_resumes_user_email_active (user_email, is_active),
    INDEX idx_resumes_user_email_created (user_email, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Notes:**
- `object_key` is server-generated UUID (e.g. `resumes/{userEmail}/{uuid}.pdf`).
  Never trust `file_name` for storage paths.
- `is_active = FALSE` means soft-deleted from user's list, but the
  MinIO object is retained because applications may reference it
  (FR-3.6).
- `idx_resumes_user_email_active` supports the "list active resumes
  for user" query (FR-3.4).

## `resume_counters`
Owns the "Resume N" auto-numbering counter (FR-3.2).

```sql
CREATE TABLE resume_counters (
    user_email   VARCHAR(255) NOT NULL,
    next_number  INT          NOT NULL DEFAULT 1,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Usage pattern:**
```sql
-- Inside transaction with the resume insert
SELECT next_number FROM resume_counters WHERE user_email = ? FOR UPDATE;
-- Use next_number as N in label "Resume N"
UPDATE resume_counters SET next_number = next_number + 1 WHERE user_email = ?;
-- Insert resume with label "Resume {N}"
```

This guarantees the counter never reuses numbers across deletes
(FR-3.2). On first upload for a new user, `INSERT ... ON DUPLICATE
KEY UPDATE` initializes the row at 1.

---

# jobdb (Job Service)

## `jobs`
```sql
CREATE TABLE jobs (
    id                 VARCHAR(36)  NOT NULL,
    recruiter_email    VARCHAR(255) NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description        TEXT         NOT NULL,
    company            VARCHAR(200) NOT NULL,
    location           VARCHAR(200),
    is_remote          BOOLEAN      NOT NULL DEFAULT FALSE,
    employment_type    ENUM('FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP') NOT NULL,
    salary_min         DECIMAL(12,2),
    salary_max         DECIMAL(12,2),
    salary_currency    VARCHAR(3)   DEFAULT 'INR',  -- ISO 4217
    skills_required    JSON         NOT NULL,        -- ["Java","Kafka",...]
    experience_min     DECIMAL(4,1),
    experience_max     DECIMAL(4,1),
    status             ENUM('DRAFT','PUBLISHED','CLOSED') NOT NULL DEFAULT 'DRAFT',
    published_at       DATETIME,
    closed_at          DATETIME,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_jobs_recruiter (recruiter_email, is_active),
    INDEX idx_jobs_status_active (status, is_active),
    INDEX idx_jobs_published_at (published_at),
    FULLTEXT INDEX ft_jobs_title_desc (title, description)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Notes:**
- `published_at` is null while in DRAFT, set on first PUBLISHED
  transition. Used for sort-by-date in search results.
- `closed_at` set when status → CLOSED. Null otherwise.
- `FULLTEXT` index on title + description supports keyword search
  (FR-6.2). MySQL fulltext is sufficient for v1; Elasticsearch is
  Phase 5 stretch.
- `skills_required` is JSON. Skill-based filtering (FR-6.3) for v1
  uses `JSON_CONTAINS`. Acknowledged slow; Matching Service handles
  the performant version.
- `is_active = FALSE` is for hard delete by recruiter (FR-5.4); CLOSED
  status is for "no longer accepting applications but still visible."
  Different semantics.

**Phase 5 — Elasticsearch migration:**

When search/filter performance becomes a real bottleneck, FR-6.2 and
FR-6.3 move to Elasticsearch:
- MySQL stays as the source of truth for jobs (writes go here first)
- Elasticsearch holds a denormalized search index, populated by
  consuming `JobPostedEvent` / `JobUpdatedEvent` / `JobClosedEvent`
- `GET /api/jobs/search` switches from MySQL to Elasticsearch
- Recruiter CRUD endpoints (`POST`, `PUT`, `DELETE`, status changes)
  continue to hit MySQL
- The `FULLTEXT` index can be dropped at that point

This is the same event-driven pattern as Matching Service — derived
view of source-of-truth data. v1 ships with MySQL FULLTEXT to keep
infrastructure light.

## `saved_jobs`
For FR-6.6 / FR-6.7.

```sql
CREATE TABLE saved_jobs (
    candidate_email  VARCHAR(255) NOT NULL,
    job_id           VARCHAR(36)  NOT NULL,
    saved_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (candidate_email, job_id),
    INDEX idx_saved_jobs_candidate (candidate_email, saved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Notes:**
- Composite PK prevents duplicate saves.
- No `is_active`; "unsave" is a real delete.
- `job_id` is not FK'd to `jobs` despite being in the same DB —
  keeping it consistent with cross-service rules. Application layer
  validates job exists.

---

# applicationdb (Application Service)

## `applications`
```sql
CREATE TABLE applications (
    id                       VARCHAR(36)  NOT NULL,
    job_id                   VARCHAR(36)  NOT NULL,    -- references jobdb.jobs.id (no FK)
    candidate_email          VARCHAR(255) NOT NULL,
    resume_id                VARCHAR(36)  NOT NULL,    -- references resumedb.resumes.id (no FK)

    -- Frozen profile snapshot at apply-time (FR-8.4, FR-8.10)
    snapshot_first_name      VARCHAR(100) NOT NULL,
    snapshot_last_name       VARCHAR(100) NOT NULL,
    snapshot_skills          JSON         NOT NULL,
    snapshot_experience      JSON         NOT NULL,
    snapshot_education       JSON         NOT NULL,
    snapshot_summary         VARCHAR(2000),
    snapshot_years_experience DECIMAL(4,1),

    -- Per-application
    cover_note               VARCHAR(5000),

    -- Status tracking
    status                   ENUM('APPLIED','REVIEWING','SHORTLISTED',
                                  'REJECTED','OFFERED','WITHDRAWN')
                                  NOT NULL DEFAULT 'APPLIED',
    applied_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status_updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,

    is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_applications_job_candidate (job_id, candidate_email),  -- FR-8.5
    INDEX idx_applications_job (job_id, status),
    INDEX idx_applications_candidate (candidate_email, applied_at),
    INDEX idx_applications_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Notes:**
- `UNIQUE (job_id, candidate_email)` enforces FR-8.5 at the DB level.
- `WITHDRAWN` status is set when candidate withdraws (FR-8.7). The
  unique constraint prevents re-applying. (If you want re-apply
  after withdrawal in the future, change to a partial-unique pattern
  or use `is_active`.)
- All `snapshot_*` columns are frozen on insert; only `cover_note`,
  `status`, and `status_updated_at` change after creation.
- `snapshot_*` JSON shapes match `userdb.users` JSON shapes exactly
  — copy as-is at apply-time.

## `cached_jobs`
Local read-model populated by `JobPostedEvent` / `JobUpdatedEvent` /
`JobClosedEvent` consumers. Used for cross-service validation
without sync HTTP calls (event-carried state transfer per NFR-6.6).

```sql
CREATE TABLE cached_jobs (
    job_id           VARCHAR(36)  NOT NULL,
    recruiter_email  VARCHAR(255) NOT NULL,
    title            VARCHAR(200) NOT NULL,
    status           ENUM('DRAFT','PUBLISHED','CLOSED') NOT NULL,
    last_synced_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (job_id),
    INDEX idx_cached_jobs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Notes:**
- Read-only from the Application Service's perspective. Updated only
  by Kafka consumer.
- Used to validate "job exists and is PUBLISHED" before accepting
  an application — without calling Job Service.
- Stale by definition (eventual consistency). Tolerable: applying to
  a CLOSED job in the milliseconds between close and event delivery
  is harmless; recruiter will see it.
- Only stores fields needed for validation, not full job data.

---

# Matching Service [PLANNED — Phase 2] — Redis (no SQL DB)

**Storage decision: Redis, not MySQL.** Rationale:
- Access pattern is "top N ranked items by score" for both directions
  (jobs for a candidate, candidates for a job). Redis sorted sets
  (`ZADD` / `ZREVRANGE`) are built for this — `O(log N)` insert,
  `O(log N + M)` range read.
- Doing this in MySQL would be reinventing sorted sets in 200 lines
  of SQL with worse performance.
- Redis is already in the stack for rate limiting (NFR-1.10).
- This is the one place polyglot persistence is genuinely justified.

## Redis key design

```
# Per-candidate ranked job matches (sorted set; score = match score 0-100)
matches:candidate:{email}            ZSET   member=jobId, score=matchScore
  Example: ZADD matches:candidate:alice@x.com 87 job-uuid-123
  Read:    ZREVRANGE matches:candidate:alice@x.com 0 19 WITHSCOPES

# Per-job ranked candidate matches (sorted set; same shape, mirrored)
matches:job:{jobId}                  ZSET   member=email, score=matchScore
  Example: ZADD matches:job:job-uuid-123 87 alice@x.com
  Read:    ZREVRANGE matches:job:job-uuid-123 0 19 WITHSCOPES

# Skill inverted index — which candidates have which skill
skill:candidates:{skillName}         SET    members=emails
  Example: SADD skill:candidates:kafka alice@x.com bob@x.com
  Used by recompute logic when a job's required skills change

# Skill inverted index — which jobs require which skill
skill:jobs:{skillName}               SET    members=jobIds
  Example: SADD skill:jobs:kafka job-uuid-123 job-uuid-456
  Used by recompute logic when a candidate's skills change

# Per-candidate skill snapshot (used by recompute)
candidate:skills:{email}             SET    members=skillNames

# Per-job snapshot (used by recompute)
job:skills:{jobId}                   SET    members=skillNames
job:meta:{jobId}                     HASH   { status, recruiterEmail, ... }

# Match explanation cache (for FR-7.2 "3 of 5 skills matched")
match:explain:{email}:{jobId}        HASH   { matchedSkills, totalRequired,
                                              matchedCount, computedAt }
```

## Recompute triggers

- `ProfileUpdatedEvent` → recompute matches for that candidate against
  all PUBLISHED jobs (or only jobs whose required skills overlap with
  the changed skills, for performance).
- `JobPostedEvent` / `JobUpdatedEvent` → recompute matches for that
  job against all candidates (or filtered by skill overlap).
- `JobClosedEvent` → `DEL matches:job:{jobId}` and remove jobId from
  every `matches:candidate:*` sorted set.

## Persistence config

- Redis configured with **AOF persistence** (`appendonly yes`,
  `appendfsync everysec`) — match data is recoverable but
  recomputable from events if lost.
- Source of truth is **the event stream**. Redis is a derived view.
  Wiping Redis and replaying events from the start should reproduce
  the same state (up to event log retention).

## No relational schema

Matching Service has no MySQL database. All state in Redis.
This is the only service in the system without a SQL DB.

---

# ai_parserdb (AI Parser Service) [PLANNED — Phase 2]

**Likely stateless** — no persistent storage needed. Parsing is
synchronous request/response. The parsed result is returned to User
Service, which hands it to the client.

If we add caching/audit later (e.g. "remember parses to avoid
re-billing the LLM for the same file"), schema will be:

```sql
-- Tentative, not built
CREATE TABLE parse_results (
    id                VARCHAR(36)  NOT NULL,
    resume_object_key VARCHAR(500) NOT NULL,
    file_hash         VARCHAR(64)  NOT NULL,    -- SHA-256 of file bytes
    llm_provider      VARCHAR(50)  NOT NULL,
    llm_model         VARCHAR(100) NOT NULL,
    parsed_data       JSON         NOT NULL,
    parsed_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_parse_file_model (file_hash, llm_provider, llm_model)
);
```

Decision to add caching deferred. v1: stateless.

---

## Summary table — what owns what

| Store | Tables / Keys | Service |
|---|---|---|
| `auth_db` (MySQL) | `users` | Auth Service |
| `userdb` (MySQL) | `users` | User Service |
| `resumedb` (MySQL) | `resumes`, `resume_counters` | Resume Service |
| `jobdb` (MySQL) | `jobs`, `saved_jobs` | Job Service |
| `applicationdb` (MySQL) | `applications`, `cached_jobs` | Application Service |
| **Redis** | `matches:*`, `skill:*`, `candidate:*`, `job:*` | Matching Service |
| `ai_parserdb` (MySQL) | none (stateless) or `parse_results` Phase 2+ | AI Parser Service |

Polyglot persistence summary:
- **MySQL** for transactional/relational services (the majority)
- **Redis** for Matching Service ranked-list workload
- **MinIO** for resume file storage (object store)
- **Elasticsearch** Phase 5 — replaces JPA `LIKE` + JSON queries in Job Service search

---

## Open questions / deferred decisions

- **Migrations:** still using `ddl-auto=update`. Phase 4+ should
  introduce Flyway or Liquibase. The DDL in this doc becomes the
  baseline V1 migration.
- **Currency handling:** `salary_currency VARCHAR(3)` defaults to
  `INR`. Multi-currency display/conversion not in scope.
- **Soft delete consistency:** every "main" entity has `is_active`.
  Join tables (`saved_jobs`) don't — true delete is correct there.
- **Audit columns:** `created_by` / `updated_by` not added. If admin
  actions become a thing (FR-10), revisit.
