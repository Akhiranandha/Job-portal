# FR-7 — AI Job Matching

## Goal

Compute candidate-↔-job match scores so that candidates see jobs ranked by fit and recruiters see applicants ranked by fit, each with a brief explanation, refreshed when profiles or jobs change.

## Requirements

- (FR-7.1) A candidate **must** see jobs ranked by fit with their profile.
- (FR-7.2) The match score **must** be visible with a brief explanation (e.g. "3 of 5 required skills matched").
- (FR-7.3) A recruiter **must** see applicants ranked by fit with the job description.
- (FR-7.4) Matches **must** update when a profile or a job changes.
- (FR-7.5) Matching **must** use **profile data only**. Parsed resume data is not persisted (see `FR-4-ai-resume-parsing.md` FR-4.8) and **must not** be a side-channel input to matching.
- (NFR-3.1) P95 read latency on the ranked-list endpoints **must** be under 500 ms (matching falls under the general read-latency target; ranked-list reads are `O(log N + M)` against Redis sorted sets).
- (NFR-3.5) Match-list endpoints **must** be paginated (sorted-set range read).
- (NFR-5.3) Heavy work **must** be async via Kafka — match recomputes happen on event consumption, not on the read path.
- (NFR-2.5) Kafka consumers **must** be idempotent. Dedupe on `(email, updatedAt)` for profile events and `(jobId, updatedAt)` for job events before mutating Redis.

## User Stories

- As a candidate, I want my home feed to show jobs ranked by how well they fit my profile so that I find good roles without typing search terms.
- As a candidate, I want each ranked job to show "X of Y required skills matched" so that I understand why it ranks where it does.
- As a recruiter, I want a list of applicants for my job ranked by fit so that I review the best matches first.
- As a candidate, I want my match list to refresh after I update my skills so that newly relevant jobs surface.
- As a recruiter, I want my applicant ranking to refresh when I edit the job's required skills so that the order reflects the current spec.

## Technical Details

- **Owning service(s):** Matching Service `:8087` `[PLANNED]` for Phase 2.
- **Data ownership:** **Redis only — no MySQL DB.** This is the only service in the system without a SQL DB. Rationale (locked in `docs/ROADMAP.md` decision log): top-N-by-score is `ZADD`/`ZREVRANGE` natively; doing it in MySQL would reinvent sorted sets badly. Redis is also already in the stack for gateway rate limiting (NFR-1.10).
- **Redis key design** (full detail in `docs/SCHEMAS.md`):
  - `matches:candidate:{email}` — `ZSET`, member = `jobId`, score = match score 0–100. `ZADD` on recompute, `ZREVRANGE 0 N WITHSCORES` for ranked read.
  - `matches:job:{jobId}` — `ZSET`, mirror of the above for the recruiter side.
  - `skill:candidates:{skillName}` — `SET` of emails. Used to recompute when a job's required skills change.
  - `skill:jobs:{skillName}` — `SET` of jobIds. Used to recompute when a candidate's skills change.
  - `candidate:skills:{email}` — `SET`, snapshot of a candidate's current skills.
  - `job:skills:{jobId}` — `SET`, snapshot of a job's required skills.
  - `job:meta:{jobId}` — `HASH` `{ status, recruiterEmail, ... }`.
  - `match:explain:{email}:{jobId}` — `HASH` `{ matchedSkills, totalRequired, matchedCount, computedAt }` for FR-7.2.
- **API surface (planned):**
  - `GET /api/matches/me` — ranked jobs for the authenticated candidate. Identity from `X-User-Email`. Paginated.
  - `GET /api/matches/job/{id}` — ranked candidates for a given job. Owner-only authz (job's `recruiter_email` must equal `X-User-Email`).
- **Events produced/consumed:**
  - Consumed: `profile-updated` (recompute that candidate's matches against `PUBLISHED` jobs), `job-posted` and `job-updated` (recompute that job's matches against all candidates), `JobClosedEvent`-equivalent (`DEL matches:job:{jobId}` and remove the `jobId` from every `matches:candidate:*`).
  - Produced: none.
- **Cross-service interactions:** Matching Service is read-only from outside — fed by Kafka, queried by HTTP. No sync HTTP calls to other services on the recompute path.
- **Algorithm v1:** Skill overlap + TF-IDF on job description (per `docs/ARCHITECTURE.md`). Score range 0–100.
- **Persistence config:** Redis configured with AOF (`appendonly yes`, `appendfsync everysec`). The event log is the source of truth — wiping Redis and replaying events should reproduce equivalent state up to event-log retention.
- **Performance optimisation:** Recompute can be scoped using the skill inverted index — when a candidate's skills change, recompute only against jobs whose `skill:jobs:{skillName}` overlaps with the changed skills, instead of every `PUBLISHED` job.
- **Status:** `[PLANNED]` — Phase 2 work. Add Redis to `docker-compose` as part of Phase 2 (also unblocks NFR-1.10 rate limiting).

## Out of Scope

- Vector embeddings / semantic match — Phase 5 stretch goal. v1 is skill overlap + TF-IDF only.
- Explanations beyond "X of Y skills matched" — natural-language reasoning over experience or education is not in scope.
- Cross-tenant ranking or fairness adjustments — not in PRODUCT.md.
- Storing parsed resume data and using it as a matching input — explicitly forbidden by FR-7.5.
- Match scoring against `DRAFT` or `CLOSED` jobs — only `PUBLISHED` jobs are matched.
- Persistence in MySQL — Matching Service has no SQL DB.
- A migration tool for Redis state — Redis is a derived view; rebuild from event log instead.
- Multi-region replication — not in scope.

## Edge Cases / Open Questions

- **Edge case:** Recompute storm when a popular skill is added or removed. A profile edit that toggles "Java" recomputes against every job whose `skill:jobs:java` set is non-empty. Consumer **must** be idempotent and dedupe on `(email, updatedAt)` so a retried event does not double-process.
- **Edge case:** Stale Redis after a profile is soft-deleted. The Matching Service does not currently consume `delete-user`. Decide: extend the event topology so soft-delete propagates to Matching (cleanup `matches:candidate:{email}`, remove email from skill sets), or accept stale entries until the next recompute. Document the choice; today's PRODUCT.md does not require this.
- **Edge case:** Newly registered candidate has no events yet. Their `matches:candidate:{email}` is empty until at least one `profile-updated` arrives. UI **must** show "complete your profile to see ranked jobs" rather than an empty feed without context.
- **Edge case:** Job closed while listed in someone's matches. `JobClosedEvent` deletes `matches:job:{jobId}` and removes the jobId from every candidate's match set. Until that event is consumed, a candidate may briefly see a closed job in their feed — acceptable per eventual-consistency model (NFR-2.4).
- **Edge case:** Redis crash with no AOF replay. Recovery path: replay the Kafka event log from retention. Plan for retention long enough to rebuild (Kafka default retention may be insufficient — see open question).
- **Edge case:** Skill name canonicalisation drift. If User Service stores "Spring Boot" and Job Service stores "spring-boot", skill overlap returns zero. Either normalise on event consumption inside Matching, or enforce normalisation upstream. Decide before Phase 2 implementation.
- **Open question:** Should `GET /api/matches/me` page through the entire ranked set (`ZREVRANGE`) or cap at top-N (e.g. top 50)? Recommend top-50 for v1.
- **Open question:** When does `ProfileUpdatedEvent` fire? Per-field (every keystroke save) is too noisy; per-form-save is the natural granularity. Decide in `FR-2-profile-management.md` implementation and document.
- **Open question:** Kafka event-log retention required for full Redis rebuild — depends on traffic. Default 7 days may be enough for v1; revisit before production.
- **Open question:** TF-IDF on job description requires a vocabulary. Build per-recompute (cheap if cached) or maintain in Redis (`HASH`)? v1 implementation choice.
