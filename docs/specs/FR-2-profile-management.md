# FR-2 — Profile Management

## Goal

Let candidates and recruiters maintain rich profile data — basic info plus role-specific fields like skills, experience, education, and job preferences for candidates, or company info for recruiters — so that other features (matching, applications, job posting authority) have the data they need.

## Requirements

- (FR-2.1) A candidate **must** be able to view their own profile.
- (FR-2.2) A candidate **must** be able to edit their own profile.
- (FR-2.3) A candidate **must** be able to add, edit, and delete skills.
- (FR-2.4) A candidate **must** be able to add, edit, and delete work experience entries.
- (FR-2.5) A candidate **must** be able to add, edit, and delete education entries.
- (FR-2.6) A candidate **must** be able to set job preferences (locations, salary range, remote flag, employment types).
- (FR-2.7) A recruiter **must** have a separate profile shape including `company_name`, `designation`, and `company_website`. Candidate-only fields (skills, experience, education, preferences) are NULL for recruiters and recruiter-only fields are NULL for candidates — both share the single `userdb.users` table.
- (NFR-1.7) Authorization **must** be enforced at the service layer. A user **must** only be able to operate on their own profile unless they are `ADMIN` (NFR-1.8).
- (NFR-3.1) P95 read latency on profile endpoints **must** be under 500 ms.
- (NFR-3.5) Any list endpoint introduced (e.g. listing all users for admin) **must** be paginated with defaults `page=0, size=20, max=100` per `CONVENTIONS.md`.
- (NFR-5.1) The User Service **must** be stateless.
- (NFR-6.2) Errors **must** follow the standard error response shape from `CONVENTIONS.md`.
- (NFR-6.3) The API boundary **must** expose DTOs (`UserResponse`, `UserUpdateRequest`, etc.), never JPA entities.
- (NFR-1.11) Every request DTO **must** carry Bean Validation annotations (`@NotNull`, `@Email`, `@Size`, `@Past`).

## User Stories

- As a candidate, I want to view my profile via `GET /api/users/me` so that I can see what recruiters and the matching service see.
- As a candidate, I want to update my skills, experience, and education so that the matching service can rank jobs against my real background.
- As a candidate, I want to set job preferences so that I can later filter results by my preferred locations, salary range, and employment type.
- As a recruiter, I want to set my company name, designation, and website so that my job postings carry credible employer information.
- As an admin, I want to list all users so that I can audit registrations and roles. *(deferred admin actions on arbitrary users — see Out of Scope)*
- As any authenticated user, I want a `403` if I attempt to read or modify someone else's profile so that I trust my own data is private.

## Technical Details

- **Owning service(s):** User Service `:8081` `[BUILT, PARTIAL]`. Profile sub-resources (FR-2.3 to FR-2.7) are `[PLANNED]` for Phase 1.
- **Data ownership:** `userdb.users` (single table for both roles). PK = `email`. Common profile fields are columns; complex shapes are JSON columns:
  - `skills` JSON — `["Java","Kafka","Spring"]`
  - `experience` JSON — `[{ company, role, startDate, endDate, description }]` (nullable `endDate` = current)
  - `education` JSON — `[{ institution, degree, field, startYear, endYear }]`
  - `job_preferences` JSON — `{ locations, salaryMin, salaryMax, currency, remote, employmentTypes }`
  - Recruiter-only columns: `company_name`, `designation`, `company_website`.
  - `years_of_experience DECIMAL(4,1)` supports half-year granularity (e.g. `2.5`).
  - Indexes: `idx_users_role`, `idx_users_active`.
- **API surface:**
  - `GET /api/users/me` — fetch caller's profile.
  - `PUT /api/users/me` — update caller's profile.
  - `DELETE /api/users/me` — soft-delete caller (sets `is_active = FALSE`, publishes `delete-user` event).
  - `GET /api/users` — `ADMIN` only.
  - Profile sub-resource endpoints for skills/experience/education/preferences and recruiter fields are `[PLANNED]` for Phase 1; final paths to be decided in implementation but should follow the `/api/users/me/<sub-resource>` convention so identity stays self-targeted.
- **Events produced/consumed:**
  - Produced: `UserRegistrationEvent` on `user-registration`, `UserDeleteEvent` on `delete-user`.
  - Produced (planned): `ProfileUpdatedEvent` on `profile-updated`, consumed by Matching Service to refresh ranked-list indexes.
- **Cross-service interactions:** Identity is taken from the `X-User-Email` header injected by the gateway (per `CONVENTIONS.md` self-targeted pattern). Mapping uses ModelMapper (strict, null-skipping) — do not mix in manual builder mapping.
- **Status:** `[BUILT]` for FR-2.1, FR-2.2 and basic CRUD/me endpoints. `[PLANNED]` for sub-resources FR-2.3 to FR-2.7 and the `profile-updated` Kafka producer.

## Out of Scope

- Resume content and parsing — see `FR-3-resume-management.md` and `FR-4-ai-resume-parsing.md`.
- Skill validation against a canonical taxonomy — not in PRODUCT.md. Skills are free-text JSON.
- Profile photo / avatar upload — not in PRODUCT.md.
- Admin actions on arbitrary user emails (FR-1.11) — deferred to Phase 5. No `{email}`-parameterised admin endpoints.
- Email verification (`is_email_verified` column) — column exists but unused; FR-1.8 deferred to Phase 5.
- Querying profiles by skill/experience for filtering — not allowed against `userdb.users` directly. The Matching Service owns that index (see `FR-7-ai-job-matching.md` and the *Querying JSON skills* note in `docs/SCHEMAS.md`).

## Edge Cases / Open Questions

- **Edge case:** JSON column shape evolution. Adding new keys to `experience`/`education` is backward-compatible; renaming or removing keys is not. Snapshot copies in `applicationdb.applications` capture the shape at apply-time, so a forward shape change leaves old snapshots in the previous shape — readers must tolerate both.
- **Edge case:** `ProfileUpdatedEvent` recompute storms. A profile edit that touches skills triggers a Matching Service recompute against every PUBLISHED job. Producer should consolidate rapid edits; consumer must be idempotent and dedupe on `(email, updatedAt)`.
- **Edge case:** Recruiter changes `company_name` while their jobs are live. Existing `jobdb.jobs.company` snapshots are not retroactively updated — that is the documented behaviour; do not back-fill.
- **Edge case:** Soft-deleted profile referenced by an existing application. The `applicationdb.applications.snapshot_*` JSON survives because it is frozen at apply-time. The recruiter "View current profile" link (FR-8.11) must handle the soft-deleted case.
- **Open question:** What is the canonical PUT shape for sub-resources — full replace of `skills` array, or per-item add/edit/delete endpoints? PRODUCT.md FR-2.3 to FR-2.5 say "add/edit/delete" which implies per-item. Decide during Phase 1 implementation and document in the service's Swagger.
- **Open question:** Multi-currency in `job_preferences` defaults to INR per `docs/SCHEMAS.md`. No conversion logic in scope.
