# FR-8 — Applications

## Goal

Let candidates apply to jobs with a chosen resume, an editable cover note, and a frozen profile snapshot, and let recruiters track and update the application lifecycle so both sides have a faithful record of what was submitted and where it stands.

## Requirements

- (FR-8.1) On clicking "Apply", a candidate **must** see a Review screen pre-filled from their profile.
- (FR-8.2) The candidate **must** be able to choose which resume to attach via a resume picker. The most recently uploaded resume **must** be pre-selected. Each option **must** show label and upload date.
- (FR-8.3) The candidate **must** be able to edit the summary and add a cover note **for this application only**. Edits **must not** modify the candidate's profile.
- (FR-8.4) On submit, the system **must** create an Application with: a frozen snapshot of the profile (first name, last name, skills, experience, education, summary, years of experience), the chosen `resumeId`, and the cover note.
- (FR-8.5) A candidate **must not** be able to apply to the same job twice — enforced by a `UNIQUE (job_id, candidate_email)` constraint on `applicationdb.applications`.
- (FR-8.6) A candidate **must** be able to view their applications with current status.
- (FR-8.7) A candidate **must** be able to withdraw an application (status `→ WITHDRAWN`).
- (FR-8.8) A recruiter **must** be able to view all applications to their jobs, sorted by match score or by date.
- (FR-8.9) A recruiter **must** be able to change application status; the candidate **must** see the updated status.
- (FR-8.10) A recruiter **must** see the profile snapshot exactly as it was at apply-time — not the live profile.
- (FR-8.11) A recruiter **must** also see a "View current profile" link alongside the snapshot.
- (FR-8.12) A recruiter **must** be able to download the resume attached to the application via its specific `resumeId`, even if the candidate has soft-deleted it from their list.
- **Status enum:** `APPLIED → REVIEWING → SHORTLISTED → REJECTED | OFFERED | WITHDRAWN`.
- (NFR-1.7) Authorization **must** apply per `CONVENTIONS.md`:
  - Candidate side: self-targeted (`/applications/me`, withdraw on own application only).
  - Recruiter side: owner-only (status changes and per-job listings allowed only when the job's `recruiter_email` equals the requester).
- (NFR-2.1) Cross-service data **must** be event-carried (job data cached locally) so a Job Service outage does not block apply (see NFR-6.6).
- (NFR-2.5) Kafka consumers **must** be idempotent. Dedupe on the natural key (`applicationId` for application events; `jobId` for job events) before mutating state.
- (NFR-6.2) Errors **must** follow the standard error response shape from `CONVENTIONS.md`.

## User Stories

- As a candidate, I want a Review screen pre-filled from my profile so that I can apply with one click after a quick check.
- As a candidate, I want to pick which of my resumes to attach so that I can send the version most relevant to this job.
- As a candidate, I want to add a cover note that applies only to this application so that I can pitch myself for this role without changing my profile summary.
- As a candidate, I want to be told when I've already applied to this job so that I don't accidentally double-apply.
- As a candidate, I want to withdraw an application so that I can take myself out of the running cleanly.
- As a recruiter, I want to see all applications to my job ranked by match score so that I review the strongest candidates first.
- As a recruiter, I want the snapshot of the profile as it was at apply-time so that I see what the candidate actually submitted, not what they edited later.
- As a recruiter, I want to also see the candidate's current profile so that I can spot meaningful updates since they applied.
- As a recruiter, I want to move an application through statuses (`REVIEWING → SHORTLISTED → OFFERED`) so that I track my pipeline.
- As a recruiter, I want to download the exact resume the candidate attached to this application even if they've since deleted it from their list, so that I have a stable record.

## Technical Details

- **Owning service(s):** Application Service `:8084` `[PLANNED]` for Phase 1.
- **Data ownership:**
  - `applicationdb.applications`:
    - PK `id VARCHAR(36)` (UUID).
    - `job_id VARCHAR(36)` (no FK; references `jobdb.jobs.id`).
    - `candidate_email VARCHAR(255)` (no FK).
    - `resume_id VARCHAR(36)` (no FK; references `resumedb.resumes.id`).
    - Frozen snapshot columns: `snapshot_first_name`, `snapshot_last_name`, `snapshot_skills JSON`, `snapshot_experience JSON`, `snapshot_education JSON`, `snapshot_summary`, `snapshot_years_experience DECIMAL(4,1)` — JSON shapes mirror `userdb.users` exactly so a copy at apply-time is the right operation.
    - `cover_note VARCHAR(5000)`.
    - `status ENUM('APPLIED','REVIEWING','SHORTLISTED','REJECTED','OFFERED','WITHDRAWN') DEFAULT 'APPLIED'`.
    - `applied_at`, `status_updated_at DATETIME`.
    - `is_active BOOLEAN DEFAULT TRUE`, `created_at`, `updated_at`.
    - Constraints/indexes: `UNIQUE KEY uq_applications_job_candidate (job_id, candidate_email)` (FR-8.5 at the DB level); `idx_applications_job (job_id, status)`; `idx_applications_candidate (candidate_email, applied_at)`; `idx_applications_status`.
  - `applicationdb.cached_jobs` — local read-model populated by Kafka consumers. Stores only the fields needed for "is this job real and `PUBLISHED`?" validation: `job_id`, `recruiter_email`, `title`, `status`, `last_synced_at`. Read-only from the service's perspective — only the Kafka consumer writes here.
- **API surface (planned, per `docs/ARCHITECTURE.md`):**
  - `POST /api/applications` — submit. Body: `jobId`, `resumeId`, `coverNote`, snapshot fields (or built server-side from User Service profile data — see Open Questions). Returns 409 on duplicate (FR-8.5).
  - `GET /api/applications/me` — candidate's applications, paginated.
  - `GET /api/applications/job/{jobId}` — recruiter view; owner-only authz against `cached_jobs.recruiter_email`. Sortable by date or match score (match score requires a side join with Matching Service — see Open Questions).
  - `PATCH /api/applications/{id}/status` — recruiter status change. Owner-only.
  - `DELETE /api/applications/{id}` — candidate withdraw (sets `status = WITHDRAWN`, not a true delete). Self-only.
  - All endpoints behind `/api/applications/**` require JWT.
- **Events produced/consumed:**
  - Produced: `ApplicationSubmittedEvent` on `application-submitted` (consumed by Matching Service and, in future, Notification Service); `ApplicationStatusChangedEvent` on `application-status-changed` (consumed by future Notification Service).
  - Consumed: `JobPostedEvent` / `JobUpdatedEvent` / `JobClosedEvent` on `job-posted` / `job-updated` to populate `cached_jobs`. Consumer **must** be idempotent and use `trusted.packages = com.jobportal.kafka_events`.
- **Cross-service interactions:**
  - Validates "job exists and is `PUBLISHED`" by reading `cached_jobs` — no sync HTTP to Job Service (event-carried state, NFR-6.6).
  - Profile snapshot data either (a) supplied by the client from the live profile fetch, or (b) fetched by Application Service from User Service via sync HTTP at apply-time. Decide during Phase 1 (see Open Questions); option (a) is simpler but trusts the client.
  - Resume download is a Resume Service responsibility (see `FR-3-resume-management.md` FR-3.8 / FR-8.12).
- **Status:** `[PLANNED]` — Phase 1.

## Out of Scope

- Notifications (email, in-app) on status change — `FR-9` deferred to Phase 5. Consumers like Notification Service are listed as future consumers of `ApplicationStatusChangedEvent` but not built in v1.
- Admin moderation of applications — `FR-10` deferred.
- Re-applying after withdraw — currently blocked by the `UNIQUE (job_id, candidate_email)` constraint. If re-apply is desired in the future, change to a partial-unique index or use `is_active` (documented escape hatch in `docs/SCHEMAS.md`).
- Offer letter / contract generation — not in PRODUCT.md.
- Application analytics dashboards — not in PRODUCT.md.
- Hard-deleting applications — `WITHDRAWN` is a status, not a row delete; the row remains so the recruiter sees the trail.
- Cross-job application search by candidate — only `GET /applications/me` is in scope.
- Match-score sorting on the recruiter list (FR-8.8) without Matching Service — open question on how to implement this when Matching Service is down or in Phase 1 before Phase 2 ships.

## Edge Cases / Open Questions

- **Edge case:** Withdraw vs. recruiter rejection race. If a candidate withdraws and the recruiter rejects in overlapping requests, `status` lands on whichever wins the `UPDATE`. Both transitions are terminal-ish; document last-write-wins and surface the actual value to both sides.
- **Edge case:** Resume deleted after application. The candidate's `is_active = FALSE` on `resumedb.resumes` does not affect the application — the application's `resume_id` reference is stable, and FR-8.12 requires the recruiter to still be able to download via that specific `resumeId`. Resume Service must honour download even when `is_active = FALSE` (see `FR-3-resume-management.md`).
- **Edge case:** Job closed while application is `REVIEWING`. The application stays valid and visible to both sides. Closing a job does **not** auto-reject open applications.
- **Edge case:** Job applied to in the milliseconds between recruiter close and `JobClosedEvent` arrival. `cached_jobs` may briefly show `PUBLISHED`. Tolerable per `docs/SCHEMAS.md` (eventual consistency); the recruiter will see the late application after close.
- **Edge case:** Idempotent consumer for `application-submitted`. Use `applicationId` as the dedupe key — re-delivery must not double-process for downstream Matching Service.
- **Edge case:** Snapshot stays frozen forever. Profile edits after apply-time **must not** modify `snapshot_*`. Only `cover_note`, `status`, and `status_updated_at` change after creation.
- **Edge case:** Soft-deleted candidate. Their `userdb.users.is_active = FALSE` does not affect existing applications; `snapshot_*` is the source for the recruiter view. The "View current profile" link (FR-8.11) must handle the soft-deleted profile case (decide: 404, "user no longer active", etc.).
- **Edge case:** Cover note size. `VARCHAR(5000)` cap; validate at the API boundary (`@Size(max = 5000)`).
- **Open question:** How does the snapshot get into the request? (a) Client sends it (simpler, trusts client). (b) Application Service fetches the profile via sync HTTP from User Service at apply-time (more correct, costs a round-trip). Recommend (b) and treat the request body as carrying only `jobId`, `resumeId`, `coverNote` plus an optional client-edited `snapshot_summary`.
- **Open question:** Sorting by match score on `GET /applications/job/{jobId}` (FR-8.8). Match scores live in Matching Service Redis. Either (a) Application Service calls Matching at request time (sync HTTP — fine here since the user is waiting and there's no event-carrying alternative), or (b) match scores are denormalised into the application row via a Kafka event from Matching. v1 should pick (a) for simplicity unless latency proves an issue.
- **Open question:** Should `ApplicationStatusChangedEvent` carry the full new state or only the delta? Future Notification Service will consume it, so include enough for an email body without re-fetching.
