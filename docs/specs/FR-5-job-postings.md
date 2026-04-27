# FR-5 — Job Postings

## Goal

Let recruiters create, edit, publish, close/reopen, and delete job postings, and let any authenticated user view published postings, so candidates have something to search and apply to and recruiters can manage their pipeline.

## Requirements

- (FR-5.1) A recruiter **must** be able to create a job posting.
- (FR-5.2) A recruiter **must** be able to edit job postings they own.
- (FR-5.3) A recruiter **must** be able to close and reopen a job posting (status transitions, see FR-5.7).
- (FR-5.4) A recruiter **must** be able to delete (soft-delete: `is_active = FALSE`) job postings they own.
- (FR-5.5) A recruiter **must** be able to view all of their own job postings, including `DRAFT` ones.
- (FR-5.6) Any authenticated user **must** be able to view `PUBLISHED` job postings.
- (FR-5.7) A job posting **must** follow the lifecycle `DRAFT → PUBLISHED → CLOSED`. `published_at` is set on first transition to `PUBLISHED`; `closed_at` is set on transition to `CLOSED`.
- (FR-5.8) A recruiter **must** be able to see the applicant count per job.
- (NFR-1.7) Authorization **must** be enforced at the service layer:
  - Only the `RECRUITER` role can create jobs (role-required pattern from `CONVENTIONS.md`).
  - Only the owning recruiter can edit, delete, or change status (owner-only pattern).
- (NFR-1.11) Request DTOs **must** carry Bean Validation annotations.
- (NFR-3.5) The list endpoints **must** be paginated (defaults `page=0, size=20, max=100`).
- (NFR-6.2) Errors **must** follow the standard error response shape from `CONVENTIONS.md`.
- (NFR-6.3) Endpoints **must** expose DTOs (`JobRequest`, `JobResponse`, etc.), never JPA entities.

## User Stories

- As a recruiter, I want to draft a job posting so that I can prepare it without making it visible to candidates yet.
- As a recruiter, I want to publish a draft so that candidates can find and apply to it.
- As a recruiter, I want to edit my published job to fix a typo so that the live posting reflects the correct details.
- As a recruiter, I want to close a job after I've hired so that I stop receiving new applications but the existing ones are still visible to me.
- As a recruiter, I want to reopen a closed job if my hire fell through so that I can resume taking applications.
- As a recruiter, I want to see the applicant count on each job so that I know which postings need triage.
- As a candidate, I want to view a published job's full description so that I can decide whether to apply.
- As any authenticated user, I want a `403` if I try to edit a job I don't own so that recruiters trust the platform.

## Technical Details

- **Owning service(s):** Job Service `:8083` `[PLANNED]` for Phase 1.
- **Data ownership:** `jobdb.jobs`:
  - PK `id VARCHAR(36)` (server-generated UUID).
  - `recruiter_email VARCHAR(255)` — owner identity (no FK; cross-service email reference).
  - `title VARCHAR(200)`, `description TEXT`, `company VARCHAR(200)` (denormalised from recruiter profile at create time — see edge cases).
  - `location VARCHAR(200)` nullable, `is_remote BOOLEAN`.
  - `employment_type ENUM('FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP')`.
  - `salary_min`, `salary_max DECIMAL(12,2)`, `salary_currency VARCHAR(3) DEFAULT 'INR'` (ISO 4217).
  - `skills_required JSON NOT NULL` — array of strings, e.g. `["Java","Kafka"]`.
  - `experience_min`, `experience_max DECIMAL(4,1)`.
  - `status ENUM('DRAFT','PUBLISHED','CLOSED') DEFAULT 'DRAFT'`.
  - `published_at`, `closed_at DATETIME` nullable.
  - `is_active BOOLEAN DEFAULT TRUE` (soft delete is distinct from `CLOSED`).
  - Indexes: `idx_jobs_recruiter (recruiter_email, is_active)`, `idx_jobs_status_active (status, is_active)`, `idx_jobs_published_at (published_at)`, `FULLTEXT ft_jobs_title_desc (title, description)` (used by FR-6 search).
- **API surface (planned, per `docs/ARCHITECTURE.md`):**
  - `POST /api/jobs` — create. `RECRUITER` only.
  - `PUT /api/jobs/{id}` — full update. Owner only.
  - `DELETE /api/jobs/{id}` — soft-delete (`is_active = FALSE`). Owner only.
  - `GET /api/jobs/{id}` — fetch single. Any authenticated user, but `DRAFT` only visible to owner.
  - `GET /api/jobs/me` — list recruiter's own jobs (any status), paginated.
  - `PATCH /api/jobs/{id}/status` — publish, close, reopen. Owner only. Sets `published_at` / `closed_at` accordingly.
  - Search and filter: see `FR-6-job-search.md`.
- **Events produced/consumed:**
  - Produced: `JobPostedEvent` on `job-posted` (consumed by Application Service for `cached_jobs` and Matching Service for ranking), `JobUpdatedEvent` on `job-updated` (consumed by Matching Service), `JobClosedEvent` (consumed by Application Service `cached_jobs` and Matching Service to wipe `matches:job:{id}`).
  - Topic naming `kebab-case`; event class names end in `Event`; payloads in `CommonModules` package `com.jobportal.kafka_events`.
- **Cross-service interactions:**
  - Job Service is the source of truth. Other services read job data via Kafka events (event-carried state per NFR-6.6), not sync HTTP.
  - Applicant count (FR-5.8) is a count from `applicationdb.applications` — Job Service must not query that DB directly. Either expose via Application Service `GET /applications/count?jobId=` or maintain a counter on Job Service updated by `ApplicationSubmittedEvent` consumption. Decide during Phase 1 (see open questions).
- **Status:** `[PLANNED]` — entire service is Phase 1 work.

## Out of Scope

- Job search, filtering, sorting, pagination semantics — see `FR-6-job-search.md`.
- Saved jobs (FR-6.6, FR-6.7) — owned by Job Service in `jobdb.saved_jobs` but spec'd in `FR-6-job-search.md`.
- Application listing per job — see `FR-8-applications.md`.
- Matching score / ranked applicants — see `FR-7-ai-job-matching.md`.
- Job templates — not in PRODUCT.md.
- Recruiter-to-recruiter job transfer — not in PRODUCT.md.
- Bulk import (CSV upload of jobs) — not in PRODUCT.md.
- Multi-currency conversion — `salary_currency` defaults to INR; no conversion logic (`docs/SCHEMAS.md` open question).
- Elasticsearch-based search — Phase 5 stretch, see `docs/ROADMAP.md`.

## Edge Cases / Open Questions

- **Edge case:** Status transitions on a job that already has applications. Closing a `PUBLISHED` job with active `REVIEWING`/`SHORTLISTED` applications **must not** modify those applications — they continue through the lifecycle owned by the Application Service.
- **Edge case:** Reopen after close. `closed_at` is set on close; on reopen, it should be cleared (or kept as last-closed timestamp — decide; affects search sort).
- **Edge case:** Soft-delete (FR-5.4) of a job that has applications. The `applicationdb.applications` rows still reference `job_id`; recruiter-side application listing must continue to work. Apply through the cached-job event (`JobClosedEvent`-like) so the Application Service can mark the cache stale; do not hard-delete in `cached_jobs`.
- **Edge case:** Recruiter is soft-deleted while their jobs are live. Jobs remain in `jobdb.jobs`. Decide whether deletion of a recruiter cascades to closing all their jobs — currently unspecified.
- **Edge case:** `company` denormalised from recruiter profile. If the recruiter changes `company_name` later, existing jobs' `company` column does not update — documented behaviour of the snapshot-at-create approach.
- **Edge case:** Direct edit of `published_at` / `closed_at` from the API. These **must** be server-managed, never client-supplied.
- **Edge case:** Skill array order in `skills_required` — does order imply priority? Treat as a set for matching; preserve insertion order for display only.
- **Open question:** Where does the applicant count for FR-5.8 live? Options: (a) sync HTTP from Job Service to Application Service, (b) Job Service maintains a counter updated by Kafka consumer of `ApplicationSubmittedEvent` and `ApplicationStatusChangedEvent` (for `WITHDRAWN`). Option (b) aligns with NFR-6.6 (event-carried state). Decide during Phase 1.
- **Open question:** Should `GET /api/jobs/{id}` for a `DRAFT` return 404 or 403 for non-owners? Recommend 404 to avoid confirming existence.
