# TODO — Next item

Phase 0 housekeeping closed (CORS on the Gateway, externalized MySQL credentials, `/auth/password` auth-required). FR-2.3..2.7 profile sub-resources landed in User Service with `ProfileUpdatedEvent` producer wired. NFR-1.10 (login rate limiting) stays parked behind Redis in Phase 2. See [docs/ROADMAP.md](docs/ROADMAP.md).

## 1. Phase 1: Job Service (FR-5 + FR-6 v1)

First brand-new Phase 1 service. Spec: [docs/specs/FR-5-job-postings.md](docs/specs/FR-5-job-postings.md) and [docs/specs/FR-6-job-search.md](docs/specs/FR-6-job-search.md).

- New service `:8083`, owns `jobdb` (DDL already in [docs/SCHEMAS.md](docs/SCHEMAS.md)).
- CRUD: `POST /api/jobs`, `PUT /api/jobs/{id}`, `DELETE /api/jobs/{id}`, `GET /api/jobs/{id}`, `GET /api/jobs/me`.
- Status transitions: `PATCH /api/jobs/{id}/status` for publish / close / reopen. Set `published_at` / `closed_at` server-side.
- Search: `GET /api/jobs/search` using MySQL FULLTEXT on `(title, description)` + `JSON_CONTAINS` on `skills_required`. Elasticsearch is Phase 5 — don't reach for it.
- Saved jobs: `POST/DELETE /api/jobs/{id}/save`, `GET /api/saved-jobs/me`.
- Publish `JobPostedEvent`, `JobUpdatedEvent`, `JobClosedEvent` to Kafka. Add the topics to `docker-compose.yaml` `kafka-init`.
- Authz: only `RECRUITER` creates jobs; only the owning recruiter edits / deletes / changes status; any authenticated user can view `PUBLISHED`.

Once Job Service is up, Resume Service (FR-3, with MinIO) and Application Service (FR-8) follow in either order to complete Phase 1.

---

Not committed — local working file.
