# Job Portal — Claude Code Working Notes

This is a microservices-based job portal demonstrating distributed systems,
event-driven design, and AI-assisted resume parsing. **Read these docs
before making changes.**

## Where to look first

| Question                                                      | File                   |
| ------------------------------------------------------------- | ---------------------- |
| What are we building (features)?                              | `docs/PRODUCT.md`      |
| Per-feature specs (Goal, Reqs, Stories, Tech, Out of scope, Edge cases) | `docs/specs/`          |
| How is the system designed?                                   | `docs/ARCHITECTURE.md` |
| What does each table look like?                               | `docs/SCHEMAS.md`      |
| Coding rules for this project                                 | `docs/CONVENTIONS.md`  |
| What's done, what's next                                      | `docs/ROADMAP.md`      |

## Most important things to know

1. **Email is the user ID across services.** Two user tables exist
   (`auth_db.users`, `userdb.users`), kept in sync by Kafka events.
   There is no surrogate user ID.

2. **Phase 0 security work is mostly done.** NFR-1.3, NFR-1.5, and
   NFR-1.8 are fixed. NFR-1.6 (gateway HMAC-signs identity headers,
   services verify) is **deferred to Phase 5 stretch** — downstream
   services still trust `X-User-Email` / `X-User-Role` without
   signature verification. Don't "fix" deferred items spontaneously;
   see `docs/ROADMAP.md`.

3. **Resume parsing is synchronous via HTTP, not async via Kafka.**
   This is intentional. See FR-4 in `docs/PRODUCT.md`.

4. **Cross-service data flows event-first.** When Service A needs data
   owned by Service B, prefer event-carried state (cache locally from
   events) over sync HTTP calls. The exception is resume parsing.

5. **Polyglot persistence is deliberate, not accidental.**
   - **MySQL** is the default for transactional services
     (Auth, User, Resume, Job, Application).
   - **Redis** is used by Matching Service (sorted sets for ranked
     match lists) and by the Gateway for rate limiting.
   - **MinIO** stores resume files (S3-compatible).
   - **Elasticsearch** is Phase 5 only — Job Service starts with
     MySQL FULLTEXT and migrates later.
   - Don't reach for a different store without justifying it against
     access patterns. Data shape alone isn't a reason. See
     `docs/SCHEMAS.md` for per-service rationale.

6. **Skills, experience, education are JSON columns.** Stored in
   `userdb.users` and snapshotted into `applicationdb.applications`.
   They are _not_ queryable for matching — Matching Service consumes
   events and builds its own indexed view in Redis. Don't try to
   query JSON columns for filtering in source-of-truth services.

7. **`CommonModules` is a non-Spring shared library** for Kafka event
   DTOs. Must be `mvn install`-ed before consumer/producer services build.
   New event classes go here in package `com.jobportal.kafka_events`.

## Working style

- When asked to implement an FR, look up the FR ID in `PRODUCT.md`
  for the precise spec. The chat conversation is not the source of
  truth; the docs are.
- When designing a new endpoint, check `CONVENTIONS.md` for the error
  response shape, DTO style, and authz pattern.
- When in doubt about whether a feature is in scope, check `ROADMAP.md`
  for phase. If it's deferred, don't build it.
- Do not add features beyond the spec. If a "nice to have" comes to
  mind, surface it as a question; don't implement it.

## Tech stack quick reference

- **Java 21**, Spring Boot 3.5.13, Spring Cloud 2025.0.2
- **MySQL 8** (localhost:3306, not containerised currently) — transactional services
- **Redis** (planned) — Matching Service + Gateway rate limiting
- **Kafka** (KRaft mode, port 9092, via docker-compose) — event bus
- **MinIO** (planned) — resume file storage
- **Elasticsearch** (Phase 5 only) — Job Service search migration
- Each service is an independent Maven project (no parent POM)

## Service ports

| Service                    | Port | Status          |
| -------------------------- | ---- | --------------- |
| discovery-service (Eureka) | 8761 | built           |
| api-gateway                | 8080 | built (partial) |
| user-service               | 8081 | built (partial) |
| auth-service               | 8082 | built           |
| job-service                | 8083 | planned         |
| application-service        | 8084 | planned         |
| resume-service             | 8085 | planned         |
| ai-parser-service          | 8086 | planned         |
| matching-service           | 8087 | planned         |

## Startup order

Kafka → discovery-service → auth-service + user-service → api-gateway

Other services join as they're built.

Don't run tests after each change unless i explicitly tell you to.
