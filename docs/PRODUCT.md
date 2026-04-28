# Job Portal — Product Requirements

**Version:** 1.0 (locked)
**Last updated:** 2026-04-25

A microservices-based job portal with AI-assisted resume parsing.
This document is the source of truth for *what* we're building.
For *how*, see `ARCHITECTURE.md`.

---

## Functional Requirements

### FR-1. Identity & Access
- **FR-1.1** User can register as a candidate ✅ done
- **FR-1.2** User can register as a recruiter
- **FR-1.3** User can log in with email + password ✅ done
- **FR-1.4** User can log out (token revocation)
- **FR-1.6** User can change password ✅ done
- **FR-1.9** System enforces role-based access (JOB_SEEKER / RECRUITER / ADMIN)
- **FR-1.10** User can soft-delete their account ✅ done

*Deferred:* FR-1.5 refresh tokens, FR-1.7 password reset, FR-1.8 email verification, FR-1.11 admin user management

### FR-2. Profile Management
- **FR-2.1** Candidate can view their profile ✅ done
- **FR-2.2** Candidate can edit their profile ✅ done
- **FR-2.3** Candidate can add/edit/delete skills ✅ done
- **FR-2.4** Candidate can add/edit/delete work experience entries ✅ done
- **FR-2.5** Candidate can add/edit/delete education entries ✅ done
- **FR-2.6** Candidate can set job preferences (location, salary range, remote, employment type) ✅ done
- **FR-2.7** Recruiter has a separate profile (company, designation, company website) ✅ done

### FR-3. Resume Management (Multi-Resume, Cap 5)
- **FR-3.1** Candidate can have up to 5 active resumes (PDF/DOCX, ≤5MB each)
- **FR-3.2** Each resume is auto-labeled "Resume N" using a per-user counter that always increments. Deletion does not reuse numbers.
- **FR-3.3** Candidate can rename a resume label (max 50 chars)
- **FR-3.4** Candidate can view their list of resumes (label + upload date, newest first)
- **FR-3.5** Candidate can download any of their resumes
- **FR-3.6** Candidate can soft-delete a resume. If attached to applications, the file is retained so recruiters can still download.
- **FR-3.7** File type (PDF/DOCX) and size (≤5MB) validated on upload
- **FR-3.8** Recruiter can download the resume attached to an application
- **FR-3.9** When user is at cap (5/5) and uploads a new resume, system shows a FIFO confirmation prompt: *"Your oldest resume (Resume X, uploaded [date]) will be soft-deleted. Continue?"* On confirm, oldest is soft-deleted and new resume saved.

### FR-4. AI Resume Parsing (Autofill)
- **FR-4.1** Profile edit screen has an "Autofill from resume" action with two modes: pick existing resume from list, OR upload a new file.
- **FR-4.2** New-file mode triggers two flows sequentially: (a) save to resume list (with FIFO prompt if at cap), (b) parse via LLM.
- **FR-4.3** Existing-resume mode triggers only the parse flow against the chosen resume.
- **FR-4.4** Backend orchestrates both flows behind a single endpoint. Client makes one call and receives `{ resumeId, parsedData }`.
- **FR-4.5** If save fails, parse is not attempted. Error returned to client.
- **FR-4.6** If save succeeds but parse fails, response includes `resumeId` and parse error. Client offers retry against the now-saved resume.
- **FR-4.7** Parsed output: skills, work experience entries, education entries, summary, total years of experience.
- **FR-4.8** Parsed data populates the profile form. Profile is updated **only** when candidate explicitly saves the form.
- **FR-4.9** Cross-service call from User Service → AI Parser Service uses HTTP (not Kafka). Parsing is synchronous from the user's POV.

### FR-5. Job Postings
- **FR-5.1** Recruiter can create a job posting
- **FR-5.2** Recruiter can edit their own job postings
- **FR-5.3** Recruiter can close/reopen a job posting
- **FR-5.4** Recruiter can delete their own job postings
- **FR-5.5** Recruiter can view all their job postings
- **FR-5.6** Any authenticated user can view published job postings
- **FR-5.7** Job posting has a lifecycle: DRAFT → PUBLISHED → CLOSED
- **FR-5.8** Recruiter can see applicant count per job

### FR-6. Job Search & Discovery
- **FR-6.1** Candidate can browse published jobs
- **FR-6.2** Candidate can search by keyword
- **FR-6.3** Candidate can filter by location, skills, salary, employment type, remote
- **FR-6.4** Results are paginated
- **FR-6.5** Results are sortable (date, relevance, salary)
- **FR-6.6** Candidate can save a job for later
- **FR-6.7** Candidate can view their saved jobs

### FR-7. AI Job Matching
- **FR-7.1** Candidate sees jobs ranked by fit with their profile
- **FR-7.2** Match score is visible with brief explanation ("3 of 5 required skills matched")
- **FR-7.3** Recruiter sees applicants ranked by fit with the job description
- **FR-7.4** Matches update when profile or job changes
- **FR-7.5** Matching uses **profile data only** (parsed resume data is not persisted; see FR-4)

### FR-8. Applications
- **FR-8.1** Candidate clicks "Apply" → Review screen pre-filled from profile
- **FR-8.2** Candidate selects which resume to attach from the resume picker (most recently uploaded pre-selected; shows label + upload date)
- **FR-8.3** Candidate can edit summary + add cover note **for this application only** (does not modify profile)
- **FR-8.4** On submit, Application is created with: profile snapshot + chosen `resumeId` + cover note
- **FR-8.5** Candidate cannot apply to the same job twice (unique on `jobId + candidateEmail`)
- **FR-8.6** Candidate can view their applications with current status
- **FR-8.7** Candidate can withdraw an application (status → WITHDRAWN)
- **FR-8.8** Recruiter can view all applications to their jobs, sorted by match score or date
- **FR-8.9** Recruiter can change application status; candidate sees updates
- **FR-8.10** Recruiter sees the profile snapshot as it was at apply-time
- **FR-8.11** Recruiter sees a "View current profile" link alongside the snapshot
- **FR-8.12** Recruiter can download the resume attached to the application (specific `resumeId`, even if soft-deleted from candidate's list)

### Application status values
`APPLIED → REVIEWING → SHORTLISTED → REJECTED | OFFERED | WITHDRAWN`

*Deferred:* FR-9 Notifications, FR-10 Admin

---

## Non-Functional Requirements

### NFR-1. Security
- **NFR-1.1** All external traffic goes through the API gateway
- **NFR-1.2** Passwords hashed with BCrypt strength ≥ 10 ✅ done
- **NFR-1.3** Raw passwords never travel over Kafka or appear in logs ✅ done (User Service hashes; `UserRegistrationEvent.passwordHash` carries the hash)
- **NFR-1.4** JWT access tokens short-lived (≤3 hours); refresh tokens rotated *(refresh deferred)*
- **NFR-1.5** JWT secret loaded from environment; non-dev startup fails if unset ✅ done (auth-service and gateway both fail-fast in `@PostConstruct` when the dev default is in use outside the `dev` profile, or the secret is blank/<32 bytes)
- **NFR-1.6** Downstream services verify the request came from the gateway (not just trust headers) ⏸️ **deferred to Phase 5**
- **NFR-1.7** Role-based authorization enforced at service layer, not just gateway
- **NFR-1.8** Users can only modify their own resources unless ADMIN ✅ done (User Service; defense-in-depth — header signature verification deferred to Phase 5 under NFR-1.6)
- **NFR-1.9** CORS configured explicitly on gateway
- **NFR-1.10** Rate limiting on `/auth/login` and `/auth/register`
- **NFR-1.11** Input validation on every endpoint; entities never exposed externally ✅ done
- **NFR-1.12** File uploads size-capped, type-checked, stored with generated keys (not user-provided names)

### NFR-2. Reliability
- **NFR-2.1** Single service crash must not bring down the platform
- **NFR-2.2** Kafka consumers use retry + DLQ ✅ done in auth-service
- **NFR-2.3** External calls (LLM, MinIO, cross-service HTTP) wrapped in timeout + retry + circuit breaker
- **NFR-2.4** Registration is eventually consistent; UI handles "credentials not yet provisioned" gracefully
- **NFR-2.5** Events are idempotent on the consumer side ✅ partial

### NFR-3. Performance
- **NFR-3.1** P95 read latency < 500ms (excluding search and parsing)
- **NFR-3.2** P95 job search latency < 1s
- **NFR-3.3** Resume parsing completes within 30s; user shown loading state
- **NFR-3.4** Handles ≥100 concurrent users on laptop demo
- **NFR-3.5** All list endpoints paginated

### NFR-4. Observability
- **NFR-4.1** Structured JSON logs with correlationId, userId, service
- **NFR-4.2** Correlation ID injected at gateway, propagated through HTTP and Kafka
- **NFR-4.3** Distributed tracing via OpenTelemetry (gateway → service → Kafka → service)
- **NFR-4.4** Prometheus metrics on every service
- **NFR-4.5** Grafana dashboard: request rate, error rate, P95 latency, Kafka consumer lag
- **NFR-4.6** Health endpoints on every service

### NFR-5. Scalability
- **NFR-5.1** Services are stateless
- **NFR-5.2** Database-per-service ✅ done
- **NFR-5.3** Heavy work (matching) is async via Kafka
- **NFR-5.4** File storage is object-store-based (MinIO)

### NFR-6. Maintainability
- **NFR-6.1** OpenAPI/Swagger spec per service
- **NFR-6.2** Consistent error response schema across services (see `CONVENTIONS.md`)
- **NFR-6.3** DTOs, not entities, on the API boundary ✅ done
- **NFR-6.4** Unit tests on service layer; integration tests via Testcontainers
- **NFR-6.5** Shared event schemas in one versioned module ✅ done (CommonModules)
- **NFR-6.6** No sync HTTP for cross-service data that can be event-carried (exception: resume parsing, FR-4.9)

### NFR-7. DevOps
- **NFR-7.1** Whole stack runs via `docker-compose up`
- **NFR-7.2** CI pipeline: build, test, Docker image publish
- **NFR-7.3** Configs externalized via env vars
- **NFR-7.4** Secrets never committed

### NFR-8. Portability
- **NFR-8.1** LLM provider abstraction (`LlmClient` interface with swappable impls — Groq, OpenAI, Anthropic, Ollama)
- **NFR-8.2** S3-compatible storage (MinIO local, S3-compatible in prod)

---

## Status Snapshot

| | Count |
|---|---|
| FRs done | 8 |
| FRs to build | 47 |
| FRs deferred | 6 |
| NFRs done | 5 |
| NFRs to build | 28 |
| NFRs violated (fix in Phase 0) | 4 |
