# Job Portal — Architecture

This document describes both the **target design** and the **current
state**, with deltas explicitly marked. Tags:

- `[BUILT]`     — implemented and matches target
- `[PARTIAL]`   — implemented but incomplete or has known gaps
- `[VIOLATED]`  — implemented incorrectly; scheduled for Phase 0 fix
- `[DEFERRED]`  — known gap, fix consciously postponed (see ROADMAP)
- `[PLANNED]`   — not yet built; spec only

---

## System overview

```
                          Client (web frontend, future)
                                     │
                              ┌──────▼──────┐
                              │  API Gateway │  :8080  [BUILT, PARTIAL]
                              │  (WebFlux)   │         JWT validation; no
                              │              │         CORS, no rate limiting
                              └──────┬──────┘
                                     │
   ┌────────────┬────────────┬───────┼────────┬────────────┬────────────┐
   │            │            │       │        │            │            │
┌──▼──┐    ┌────▼───┐    ┌───▼──┐ ┌──▼──┐ ┌──▼───┐    ┌────▼────┐  ┌────▼────┐
│Auth │    │ User   │    │ Job  │ │ App │ │Resume│    │ AI      │  │Matching │
│:8082│    │:8081   │    │:8083 │ │:8084│ │:8085 │    │ Parser  │  │:8087    │
│[B]  │    │[B,P]   │    │[PLAN]│ │[PLAN│ │[PLAN]│    │:8086    │  │[PLAN]   │
│     │    │        │    │      │ │NED] │ │      │    │[PLAN]   │  │         │
└──┬──┘    └───┬────┘    └──┬───┘ └─┬───┘ └──┬───┘    └────┬────┘  └────┬────┘
   │           │            │       │        │             │            │
┌──▼──┐    ┌───▼────┐    ┌──▼───┐ ┌─▼───┐ ┌─▼──┐       ┌───▼──┐    ┌───▼────┐
│auth_│    │ user   │    │ job  │ │app  │ │resu│       │ ai_  │    │ Redis  │
│ db  │    │  db    │    │  db  │ │ db  │ │medb│       │parser│    │(sorted │
│MySQL│    │ MySQL  │    │ MySQL│ │MySQL│ │MySQL│      │  db  │    │ sets)  │
└─────┘    └────────┘    └──────┘ └─────┘ └────┘       └──────┘    └────────┘

   Eureka :8761 [BUILT]   Kafka [BUILT]   MinIO [PLANNED]   Redis (shared) [PLANNED]
                                                            (Matching + Gateway rate limiting)
```

---

## Services

### Discovery (Eureka) `:8761` [BUILT]
Standalone Eureka server. All services register here. Gateway resolves
routes via `lb://SERVICE-NAME`. No security on the dashboard.

### API Gateway `:8080` [BUILT, PARTIAL]
Single public entry point. Spring Cloud Gateway (WebFlux).

**Current routes:** (matched in declaration order; first match wins)
- `/auth/**` → AUTH-SERVICE (no JWT filter; login/password change open)
- `/api/users/public/**` → USER-SERVICE (no filter; public registration)
- `/api/users/**` → USER-SERVICE (JWT filter)

**Current behavior:** JWT filter validates HMAC-signed tokens, injects
`X-User-Email` and `X-User-Role` headers downstream.

**Gaps to close:**
- No CORS config (NFR-1.9)
- No rate limiting (NFR-1.10)
- No correlation ID injection (NFR-4.2)
- Routes for jobs/applications/resumes not yet added

### Auth Service `:8082` [BUILT]
Owns `auth_db`. Issues JWTs, manages credentials.

**Endpoints:**
- `POST /auth/login` — email + password → JWT
- `PUT /auth/password` — change password (open per current security config)

**Kafka consumers:**
- `user-registration` → creates User row (stores `passwordHash` from event verbatim — User Service is the BCrypt origin)
- `delete-user` → soft-deletes User (sets `isActive=false`)

**Known issues:**
*(none currently — Phase 0 fixes have closed the open violations on
this service)*

### User Service `:8081` [BUILT, PARTIAL]
Owns `userdb`. User profile CRUD.

**Endpoints:**
- `POST /api/users/public/register` — public registration
- `GET /api/users` — list all users (ADMIN only)
- `GET /api/users/me` — fetch caller's own profile
- `PUT /api/users/me` — update caller's own profile
- `DELETE /api/users/me` — soft-delete caller's own account

Caller identity is taken from the `X-User-Email` header injected
by the gateway. There are no `{email}`-parameterised endpoints —
admin user-management against arbitrary emails is deferred to
FR-1.11.

**Kafka producers:**
- Publishes `UserRegistrationEvent` to `user-registration` on register
- Publishes `UserDeleteEvent` to `delete-user` on delete

**Known issues:**
- `[DEFERRED NFR-1.6]` Trusts `X-User-Email` and `X-User-Role` headers
  without verifying request came from gateway. Spoofable if port 8081
  is reachable directly. Deferred to Phase 5 — mitigated today by
  network-level access control (services bind to localhost in dev).

**Recently fixed:**
- `[BUILT NFR-1.8]` Self-targeted endpoints (`/api/users/me`) take
  caller identity from the gateway-injected `X-User-Email` header,
  so a user can only operate on their own profile by construction.
  `GET /api/users` is ADMIN-only. Defense-in-depth — strength
  depends on NFR-1.6 header signing landing.

**Planned additions:**
- Skills, work experience, education sub-resources (FR-2.3 to FR-2.5)
- Job preferences (FR-2.6)
- Recruiter profile fields (FR-2.7)
- `POST /api/users/profile/parse-resume` autofill endpoint (FR-4)

### Job Service `:8083` [PLANNED]
Will own `jobdb`. Manages job postings (FR-5).

**Planned endpoints:**
- `POST /jobs`, `PUT /jobs/{id}`, `DELETE /jobs/{id}`
- `GET /jobs/{id}`, `GET /jobs/me` (recruiter's jobs)
- `GET /jobs/search` (keyword + filters, paginated)
- `PATCH /jobs/{id}/status` (publish, close, reopen)

**Authz:** Only RECRUITER role creates jobs. Only owner edits/deletes.
Search and view open to any authenticated user.

**Events published:**
- `JobPostedEvent`, `JobUpdatedEvent`, `JobClosedEvent`

### Application Service `:8084` [PLANNED]
Will own `applicationdb`. Tracks applications (FR-8).

**Planned endpoints:**
- `POST /applications` — submit (with `jobId`, `resumeId`, `coverNote`, snapshot fields)
- `GET /applications/me` — candidate view
- `GET /applications/job/{jobId}` — recruiter view (authz: job owner)
- `PATCH /applications/{id}/status` — recruiter updates
- `DELETE /applications/{id}` — candidate withdraws

**Cross-service data:** Caches `JobPostedEvent` and user profile data
locally to validate applications without sync HTTP calls
(event-carried state transfer).

**Events published:**
- `ApplicationSubmittedEvent`, `ApplicationStatusChangedEvent`

### Resume Service `:8085` [PLANNED]
Will own `resumedb` + MinIO bucket. Stores resume files and metadata
(FR-3).

**Planned endpoints:**
- `POST /resumes` — upload (multipart)
- `GET /resumes/me` — list
- `GET /resumes/{id}/download`
- `PATCH /resumes/{id}` — rename label
- `DELETE /resumes/{id}` — soft delete
- `GET /resumes/{id}/cap-status` — used by FIFO prompt UX

**Storage:** MinIO bucket `resumes`. Object key is server-generated
UUID, never user-supplied filename. File metadata in MySQL.

### AI Parser Service `:8086` [PLANNED]
Will own `ai_parserdb` (or be stateless — TBD). Parses resumes via
LLM (FR-4).

**Planned endpoints:**
- `POST /parse` — input: either `{ resumeId }` or `{ fileBytes }`.
  Output: structured parsed JSON.

**LLM abstraction:** `LlmClient` interface with implementations for
Groq (default for free tier), OpenAI, Anthropic, Ollama. Selected by
config.

**Resilience:** Resilience4j circuit breaker, timeout (≤30s), retry
with exponential backoff. Failures return 503 with retry-able marker.

### Matching Service `:8087` [PLANNED]
**Backed by Redis, not MySQL** (see `SCHEMAS.md` for key design).
Computes candidate ↔ job match scores (FR-7).

**Inputs:** Consumes profile updates and `JobPostedEvent` from Kafka.

**Endpoints:**
- `GET /matches/me` — ranked jobs for candidate
- `GET /matches/job/{id}` — ranked candidates for recruiter

**Algorithm:** v1 = skill overlap + TF-IDF on job description.
v2 (deferred) = embeddings.

**Why Redis:** ranked-list workload (`ZREVRANGE` for top-N matches)
maps natively to sorted sets. MySQL would reinvent this badly. The
event log is the source of truth; Redis is a derived view that can
be wiped and rebuilt by replaying events.

---

## Cross-cutting

### Identity
- **Email is the user ID.** No surrogate user IDs across services.
- Auth and User services each have their own `users` table, both
  keyed by email.
- Sync between them is via Kafka events. There is no reconciliation
  job. Eventual consistency.

### Authentication & Authorization
- **Authentication:** JWT issued by Auth Service. Validated by Gateway.
- **Authorization:** Role checks at gateway are coarse (path-based).
  Fine-grained authz (FR-1.9, NFR-1.7, NFR-1.8) lives in each
  service.
- Downstream services receive `X-User-Email` and `X-User-Role` from
  gateway. **`[DEFERRED]`** Headers should be HMAC-signed by gateway
  and verified by services (NFR-1.6) — postponed to Phase 5; today
  services trust the headers without verification.

### Event topology

| Topic | Producer | Consumer(s) |
|---|---|---|
| `user-registration` | User Service | Auth Service |
| `delete-user` | User Service | Auth Service |
| `job-posted` `[PLANNED]` | Job Service | Application Service, Matching Service |
| `job-updated` `[PLANNED]` | Job Service | Matching Service |
| `application-submitted` `[PLANNED]` | Application Service | Matching Service, (Notification later) |
| `application-status-changed` `[PLANNED]` | Application Service | (Notification later) |
| `profile-updated` `[PLANNED]` | User Service | Matching Service |

All Kafka payloads live in `CommonModules` package
`com.jobportal.kafka_events`. Consumers restrict trusted packages to
that namespace.

### Storage
- **Database per service.** No shared schemas.
- **MySQL 8** for transactional/relational services: Auth, User,
  Resume, Job, Application (originally spec'd as PostgreSQL; changed
  during build).
- **Redis** for Matching Service (ranked lists via sorted sets) and
  Gateway rate limiting. Shared infrastructure, but different keyspaces
  per use-case.
- **MinIO** for resume files. S3-compatible API.
- **Elasticsearch** Phase 5 — derived search index for Job Service,
  populated by events.
- **`ddl-auto=update`** in dev. Migrations TBD for production.

**Polyglot persistence rationale:** MySQL is the default. Redis is
introduced because Matching Service's access pattern (top-N by score)
is what sorted sets exist for, and Redis is already in the stack for
rate limiting. No other service has an access pattern that justifies
leaving MySQL.

### Resume parsing flow [PLANNED]

```
User clicks "Autofill from resume" on profile edit
   │
   ├─ Mode A: Upload new file
   │    User Service receives multipart upload
   │      ├─ Calls Resume Service POST /resumes (sync HTTP)
   │      │    Resume Service: save to MinIO + DB → returns resumeId
   │      │    (If at cap, returns 409 with FIFO prompt data;
   │      │     client confirms; second call with confirmEviction=true)
   │      └─ Calls AI Parser Service POST /parse { resumeId } (sync HTTP)
   │           AI Parser: fetch from MinIO, parse, return JSON
   │      Returns { resumeId, parsedData } to client
   │
   └─ Mode B: Use existing resume
        User Service receives { resumeId }
          └─ Calls AI Parser Service POST /parse { resumeId } (sync HTTP)
        Returns { resumeId, parsedData } to client

In both modes: parsed data is shown in the form. Profile is updated
ONLY when user explicitly saves the form.
```

### Application snapshot model [PLANNED]
- Application carries a frozen snapshot of profile data at apply-time
  (firstName, lastName, skills, experienceYears, education, summary).
- Plus `coverNote` (per-application, editable up to submission).
- Plus `resumeId` reference (latest version of that file is what
  recruiter sees, per Option X agreed in design).
- Profile edits after apply-time do NOT modify the snapshot.
- Recruiter sees snapshot + "view current profile" link (FR-8.10, FR-8.11).

---

## Phase 0: Known violations to fix

| ID | Issue | Fix approach |
|---|---|---|
| ~~NFR-1.3~~ | ~~Raw passwords on Kafka~~ | ✅ done — User Service BCrypts; event carries `passwordHash` |
| ~~NFR-1.5~~ | ~~JWT secret has dev fallback in prod~~ | ✅ done — `@PostConstruct` validator in auth-service and gateway aborts startup unless the `dev` profile is active or a non-default secret is supplied |
| NFR-1.6 | Services trust spoofable gateway headers | ⏸️ deferred to Phase 5 — gateway will HMAC-sign headers + timestamp, services verify |
| ~~NFR-1.8~~ | ~~Any user can edit any profile~~ | ✅ done — self-targeted `/me` endpoints take identity from gateway header |

Phase 0 is effectively closed (3 of 4 NFRs done; NFR-1.6 deferred).
See `ROADMAP.md` Phase 5 for the entry covering NFR-1.6 and other
deferred work.

---

## Decisions explicitly out of scope

- **Config Server** — not used. Env vars + docker-compose are enough.
- **Notification Service** — deferred (FR-9).
- **Admin Service** — deferred (FR-10).
- **Elasticsearch** — Phase 5 stretch goal. v1 search uses JPA `LIKE`.
- **Kubernetes** — Phase 5 stretch goal.
- **PostgreSQL** — original spec, now MySQL throughout.
