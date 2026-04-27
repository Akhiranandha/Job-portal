# Feature Specs

Per-feature specs in a fixed 6-section shape: **Goal**, **Requirements**, **User Stories**, **Technical Details**, **Out of Scope**, **Edge Cases / Open Questions**.

Each spec is self-contained — relevant NFRs are inlined into the feature they constrain rather than cross-referenced. The original cross-cutting docs (`PRODUCT.md`, `ARCHITECTURE.md`, `SCHEMAS.md`, `ROADMAP.md`, `CONVENTIONS.md`) remain authoritative for system-wide concerns; these specs recombine their content per feature.

## Specs

| Spec | Status | Phase |
|---|---|---|
| [FR-1 — Identity & Access](FR-1-identity-and-access.md) | `[BUILT, PARTIAL]` | Pre-Phase 0 + Phase 0 closed; refresh tokens / password reset / email verification / HMAC headers deferred to Phase 5 |
| [FR-2 — Profile Management](FR-2-profile-management.md) | `[BUILT, PARTIAL]` | Phase 1 closes sub-resources (skills, experience, education, preferences, recruiter fields) |
| [FR-3 — Resume Management](FR-3-resume-management.md) | `[PLANNED]` | Phase 1 |
| [FR-4 — AI Resume Parsing](FR-4-ai-resume-parsing.md) | `[PLANNED]` | Phase 2 |
| [FR-5 — Job Postings](FR-5-job-postings.md) | `[PLANNED]` | Phase 1 |
| [FR-6 — Job Search & Discovery](FR-6-job-search.md) | `[PLANNED]` | Phase 1 (MySQL FULLTEXT); Elasticsearch deferred to Phase 5 |
| [FR-7 — AI Job Matching](FR-7-ai-job-matching.md) | `[PLANNED]` | Phase 2 (Redis-backed) |
| [FR-8 — Applications](FR-8-applications.md) | `[PLANNED]` | Phase 1 |

## Where else to look

- Phase ordering, exit criteria, and locked-in design decisions: [`../ROADMAP.md`](../ROADMAP.md).
- Cross-cutting coding rules (API conventions, authorization patterns, Kafka conventions, error response shape, logging, testing): [`../CONVENTIONS.md`](../CONVENTIONS.md).
- Full DDL and Redis key design: [`../SCHEMAS.md`](../SCHEMAS.md).
- System-level architecture (services, gateway, event topology, status tags): [`../ARCHITECTURE.md`](../ARCHITECTURE.md).
- The flat FR / NFR catalogue with done / deferred markers: [`../PRODUCT.md`](../PRODUCT.md).

## Notes for new specs

- One spec per feature (FR), not per service or per doc. NFRs are inlined into the feature they constrain.
- Keep the 6-section structure fixed so specs are scannable side-by-side.
- Reference FR-X.Y / NFR-X.Y IDs inline in the requirements list so traceability back to PRODUCT.md is preserved.
- Mark each requirement with `must` / `should` / `can`. Avoid "shall."
- Cross-link adjacent specs in **Out of Scope** to prevent overlap (e.g. FR-3 points at FR-4 for parsing).
- **Edge cases** are tricky situations the implementation must handle. **Open questions** are decisions that still need an answer. Mark each line clearly so reviewers know which is which.
