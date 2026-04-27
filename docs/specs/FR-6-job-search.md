# FR-6 — Job Search & Discovery

## Goal

Let candidates browse, search, filter, sort, and bookmark `PUBLISHED` job postings so they can find roles that match their interests before deciding to apply.

## Requirements

- (FR-6.1) A candidate **must** be able to browse `PUBLISHED` jobs.
- (FR-6.2) A candidate **must** be able to search jobs by keyword (matched against title and description).
- (FR-6.3) A candidate **must** be able to filter jobs by location, skills, salary, employment type, and remote flag.
- (FR-6.4) Search results **must** be paginated.
- (FR-6.5) Search results **must** be sortable by date, relevance, and salary.
- (FR-6.6) A candidate **must** be able to save a job for later.
- (FR-6.7) A candidate **must** be able to view their saved jobs.
- (NFR-3.2) P95 job-search latency **must** be under 1 second.
- (NFR-3.5) The list endpoints **must** be paginated with defaults `page=0, size=20, max=100` (per `CONVENTIONS.md`).
- (NFR-1.7) Authorization **must** apply: any authenticated user can search/browse `PUBLISHED` jobs; only the saving candidate can read their own `saved_jobs`.

## User Stories

- As a candidate, I want to browse all published jobs so that I can get a sense of what's available.
- As a candidate, I want to search "java backend remote" so that I see only jobs whose title or description mentions those terms.
- As a candidate, I want to filter by location, salary range, employment type, and remote flag so that I can narrow results to roles I'd actually take.
- As a candidate, I want to filter by required skills so that I see jobs my background fits.
- As a candidate, I want to sort by date, relevance, or salary so that I can browse in the order most useful for me right now.
- As a candidate, I want to save a job for later so that I can revisit it without re-running the search.
- As a candidate, I want to see my list of saved jobs so that I can decide which to apply to next.

## Technical Details

- **Owning service(s):** Job Service `:8083` `[PLANNED]` for Phase 1.
- **Data ownership:**
  - `jobdb.jobs` — search source (see `FR-5-job-postings.md` for full schema). Relevant for search:
    - `FULLTEXT INDEX ft_jobs_title_desc (title, description)` — supports keyword search via MySQL fulltext.
    - `idx_jobs_status_active (status, is_active)` — fast filter to `PUBLISHED` + `is_active = TRUE`.
    - `idx_jobs_published_at (published_at)` — sort by date.
    - `skills_required JSON` — filtered with `JSON_CONTAINS` in v1 (acknowledged slow; see Out of Scope for Elasticsearch migration).
  - `jobdb.saved_jobs`:
    - PK `(candidate_email, job_id)` — composite, prevents duplicate saves.
    - `saved_at DATETIME` — for sort order.
    - `idx_saved_jobs_candidate (candidate_email, saved_at)`.
    - **No `is_active`** — "unsave" is a true delete (intentional; join tables don't soft-delete per `CONVENTIONS.md`).
    - No FK to `jobs` despite same DB; cross-service rule applies.
- **API surface (planned):**
  - `GET /api/jobs/search` — keyword + filters + paginated. Filters: `location`, `skills`, `salaryMin`, `salaryMax`, `employmentType`, `remote`. Sort: `date` (default `published_at DESC`), `relevance` (fulltext score), `salary` (`salary_min` or `salary_max`).
  - `GET /api/jobs/{id}` — fetch a single published job (any authenticated user).
  - `POST /api/jobs/{id}/save` — bookmark.
  - `DELETE /api/jobs/{id}/save` — unsave (true delete in `saved_jobs`).
  - `GET /api/saved-jobs/me` — list candidate's saved jobs, sorted newest first, paginated.
  - All endpoints accept `Pageable` per `CONVENTIONS.md`.
  - Response envelope: `ApiResponse<T>` for new services per `CONVENTIONS.md`.
- **Events produced/consumed:** None directly for search. Search reads `jobdb.jobs` synchronously. (Saved jobs likewise — no events needed for v1.)
- **Cross-service interactions:**
  - Matching-driven recommendations are a separate concern — see `FR-7-ai-job-matching.md`.
  - Search filters using `JSON_CONTAINS` on `skills_required` are functional but slow at scale; the performant skill index lives in Matching Service and is **not used for search** in v1.
- **Status:** `[PLANNED]` — Phase 1.

## Out of Scope

- Elasticsearch — Phase 5 stretch goal per `docs/ROADMAP.md`. v1 ships with MySQL FULLTEXT + `JSON_CONTAINS`. The migration plan is documented in `docs/SCHEMAS.md`: MySQL stays as source of truth, ES becomes a derived view populated from `JobPostedEvent` / `JobUpdatedEvent` / `JobClosedEvent`. The `FULLTEXT` index can be dropped at that point.
- Recommendation feed — that's matching, see `FR-7-ai-job-matching.md`.
- Search analytics (top queries, click-through) — not in PRODUCT.md.
- Negative filters ("exclude X") or boolean operators in keyword search — not in scope; v1 uses MySQL FULLTEXT default behaviour.
- Geo-radius search ("within 50 km") — not in scope.
- Search across `DRAFT` or `CLOSED` jobs by candidates — only `PUBLISHED` is searchable for non-owners (see `FR-5-job-postings.md`).
- Saving DRAFT or CLOSED jobs — `saved_jobs` may reference any job_id but the UI surface is "currently published"; behaviour for saved-then-closed jobs is documented as an edge case below.

## Edge Cases / Open Questions

- **Edge case:** Pagination drift. Jobs published mid-scroll can shift page boundaries (item appears twice or is skipped). Acceptable for v1; document in API. Use a stable sort tiebreaker (`published_at DESC, id DESC`) to reduce noise.
- **Edge case:** Filter by `salaryMin`/`salaryMax` against a job whose own range is open at one end. Decide: does a job with `salary_min = 1.5M, salary_max = NULL` match a query of `salaryMax = 2M`? Document the decision in the search endpoint contract.
- **Edge case:** Mixed-currency filtering. All jobs default to INR (`salary_currency`); multi-currency conversion is not in scope. Until that changes, the salary filter assumes the same currency as the job and rows in another currency are excluded from numeric filtering.
- **Edge case:** Saved job that is later closed or soft-deleted. `saved_jobs` row still exists; `GET /api/saved-jobs/me` should return the saved item with the current job status so the candidate sees "this job is no longer accepting applications" rather than 404.
- **Edge case:** Skills filter case sensitivity. `JSON_CONTAINS` is exact-string by default. Normalise both query and stored skill values (lowercase / canonical form) consistently.
- **Edge case:** FULLTEXT minimum word length / stop words. MySQL FULLTEXT has defaults that may surprise (3-letter minimum, stop-word list). Tune `ft_min_word_len` or document the limitation.
- **Edge case:** Search result fairness. Sorting by `relevance` uses MySQL FULLTEXT's `MATCH ... AGAINST` score, which favours exact word matches; results may feel "narrow." This is a v1 limitation pending Elasticsearch.
- **Open question:** Default sort — date or relevance? PRODUCT.md FR-6.5 lists both as supported but does not specify the default. Recommend `date DESC` (newest first) when no `q` is given; `relevance DESC` when a `q` is provided.
- **Open question:** Should `GET /api/saved-jobs/me` join in current job data, or return only `(job_id, saved_at)`? Joining requires reading `jobs` from the same service (allowed — same DB); cleaner API but more bandwidth.
