# Roadmap

Phases are ordered. Don't skip ahead unless explicitly redirected.

---

## ✅ Pre-Phase 0 (done)

- Eureka discovery `:8761`
- API Gateway `:8080` with JWT validation filter
- Auth Service `:8082` (login, password change, Kafka consumers)
- User Service `:8081` (registration, profile CRUD, Kafka producers)
- Kafka stack via docker-compose
- `CommonModules` shared library for event DTOs
- Email-as-distributed-ID pattern
- Async user registration via events

---

## ✅ Phase 0 — Foundation fixes (closed)

Goal was to fix the four violated NFRs before building new services
on top of broken foundations. Three are done; NFR-1.6 is consciously
deferred to Phase 5.

| Order | NFR | Description | Effort | Status |
|---|---|---|---|---|
| 1 | NFR-1.8 | Self-or-admin authz in User Service | ~half day | ✅ done |
| 2 | NFR-1.3 | Move BCrypt hashing to User Service; remove raw password from Kafka events | ~half day | ✅ done |
| 3 | NFR-1.5 | Fail-fast JWT secret validation in non-dev profiles | ~30 min | ✅ done |
| 4 | NFR-1.6 | Gateway signs headers, services verify (HMAC) | ~1 day | ⏸️ deferred to Phase 5 |

Plus housekeeping:
- ✅ CORS config on the Gateway (NFR-1.9) — `CorsWebFilter` bean in `apigatway`, origins via `CORS_ALLOWED_ORIGINS`
- ⏸️ Rate limiting on `/auth/login` (NFR-1.10) — blocked on Redis, picked up in Phase 2
- ✅ MySQL credentials externalized to `${DB_USERNAME}` / `${DB_PASSWORD}` (+ `DB_HOST` / `DB_PORT`)
- ✅ `/auth/password` requires authentication — gateway applies `JwtAuthentication` filter, controller derives email from `X-User-Email`, body no longer carries `email`

**Exit criteria:** the 3 fixed NFRs marked `[BUILT]` in
`ARCHITECTURE.md`; NFR-1.6 marked `[DEFERRED]`. Phase 1 can start.

---

## Phase 1 — Core domain services

Build the actual job portal: posting jobs, uploading resumes, applying.

1. **Job Service** `:8083` — FR-5 (postings) + FR-6 (search/discovery,
   v1 with JPA `LIKE`)
2. **Resume Service** `:8085` + MinIO — FR-3
3. **Application Service** `:8084` — FR-8 (with profile snapshot model)

Cross-cutting in this phase:
- Each service uses event-carried state for cross-service data
  (e.g. Application Service caches `JobPostedEvent` instead of
  calling Job Service)
- Pagination on every list endpoint from day one (NFR-3.5)
- Authz patterns from `CONVENTIONS.md` applied consistently
- New events go in `CommonModules` (`mvn install` after each addition)

**Profile sub-resources** (FR-2.3 to FR-2.7) ✅ done — five full-replace
PUT endpoints on User Service, plus `ProfileUpdatedEvent` producer ready
for Matching Service consumption in Phase 2.

**Exit criteria:** end-to-end demo possible — register → login → post
job → upload resume → apply → recruiter sees application → recruiter
updates status.

---

## Phase 2 — AI differentiator

The features that make this project stand out from a generic Spring
microservices portfolio.

1. **AI Parser Service** `:8086` — FR-4
   - `LlmClient` interface with Groq (default), OpenAI, Anthropic,
     Ollama implementations
   - PDF text extraction via PDFBox; DOCX via Apache POI
   - Resilience4j: timeout, retry, circuit breaker
   - JSON schema-enforced LLM output
2. **Wire autofill into User Service** — FR-4 endpoint
3. **Matching Service** `:8087` — FR-7, **backed by Redis (not MySQL)**
   - Add Redis to `docker-compose` (also unblocks gateway rate limiting,
     NFR-1.10)
   - Consumes `JobPostedEvent` + profile updates
   - Sorted sets for ranked match lists, plus skill inverted indexes
   - v1 algorithm: skill overlap + TF-IDF
   - Endpoints for candidate-side and recruiter-side ranking

**Exit criteria:** candidate uploads PDF → profile autofills →
candidate sees ranked job matches → applies → recruiter sees ranked
applicants.

---

## Phase 3 — Frontend

Next.js + Tailwind. Three views minimum:
- Candidate: browse / apply / matches / profile
- Recruiter: post jobs / view applicants with match scores
- Shared: login / register / profile edit

**Goal:** working demo, not pixel-perfect design. Resist UI polish.

---

## Phase 4 — Production-engineering layer

The maturity layer that pushes the project from "built it" to "built
it like a senior engineer would."

- **Observability:** OpenTelemetry tracing across HTTP + Kafka,
  Prometheus metrics, Grafana dashboard (NFR-4)
- **Resilience4j everywhere external:** LLM calls, MinIO,
  cross-service HTTP (NFR-2.3)
- **Docker Compose** for the whole stack: one command runs it all
  (NFR-7.1)
- **GitHub Actions CI:** build, test, Docker image publish (NFR-7.2)
- **OpenAPI specs** per service (NFR-6.1)

---

## Phase 5 — Stretch (pick based on energy)

Things to add if time allows. None of these are required for a strong
portfolio piece.

- **Elasticsearch migration for Job Service search** — replaces JPA
  `LIKE` + JSON `JSON_CONTAINS` filters. MySQL stays as source of
  truth; Elasticsearch is a derived search index populated by
  consuming `JobPostedEvent` / `JobUpdatedEvent` / `JobClosedEvent`.
  Same event-driven derived-view pattern as Matching Service.
- **Kubernetes** manifests + minikube deployment
- **Notification Service** (FR-9) — email + in-app on application
  status changes via Kafka
- **Embeddings-based semantic matching** instead of skill overlap
- **Real cloud deployment** (AWS / GCP)
- **Refresh tokens + email verification + password reset**
  (the deferred FR-1.5, FR-1.7, FR-1.8)
- **Gateway-signed identity headers (NFR-1.6)** — gateway HMACs
  `email|role|timestamp`, downstream services verify signature before
  trusting. Defense in depth; today services rely on the gateway
  being the only reachable entry point.

---

## Rough timeline

At 15+ hrs/week:

| Phase | Duration |
|---|---|
| Phase 0 | 1 week |
| Phase 1 | 3 weeks |
| Phase 2 | 2 weeks |
| Phase 3 | 1–2 weeks |
| Phase 4 | 1–2 weeks |
| Phase 5 | optional |

**~2.5 months to a complete, demo-able, portfolio-worthy system.**

---

## Decision log (for context drift prevention)

Major design decisions made during planning, locked in:

| Decision | Rationale |
|---|---|
| Email is distributed user ID | Simpler than synthetic IDs, no DB join across services |
| Database per service, MySQL | Honest microservices isolation |
| Resume parsing is sync HTTP, not async Kafka | User is waiting; async adds polling for no benefit |
| Application carries profile snapshot | Recruiter sees what was applied with, not live profile |
| Application resume is "latest version" reference (Option X) | Simplification; documented tradeoff |
| Multi-resume cap of 5, FIFO eviction with confirmation prompt | Balance flexibility and abuse protection |
| Auto-labeled "Resume N" with always-incrementing counter | Zero-friction labeling, optional rename |
| LLM provider abstraction via `LlmClient` interface | Vendor lock-in protection; portfolio talking point |
| Event-carried state transfer for cross-service data | Avoid sync HTTP fan-out |
| No Config Server | Reduce ceremony, focus on what's demonstrable |
| Skills/experience/education as JSON columns | Source-of-truth services stay simple; Matching Service owns queryable index |
| snake_case column names | MySQL Linux portability; consistent with existing tables |
| Polyglot persistence: MySQL default + Redis for Matching | Sorted sets are the right tool for ranked-list workload; Redis already needed for rate limiting |
| Elasticsearch deferred to Phase 5 as a migration | v1 ships in MySQL FULLTEXT; migration story stronger than picking ES day-one |
