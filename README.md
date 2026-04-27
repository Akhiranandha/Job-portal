# Job Portal

A microservices-based job portal demonstrating distributed systems, event-driven design, and AI-assisted resume parsing. Candidates manage profiles and resumes, recruiters post jobs, an AI parser auto-fills profiles from a resume, and a Redis-backed matching service ranks candidates ↔ jobs.

**Status:** Phase 0 (foundation fixes) effectively closed; Phase 1 (core domain services — Job, Resume, Application) not yet started. See [docs/ROADMAP.md](docs/ROADMAP.md) for phase ordering and exit criteria.

## Where to look

| If you want to... | Go to |
| --- | --- |
| Run the system locally | [docs/SETUP.md](docs/SETUP.md) |
| See what we're building (FRs / NFRs) | [docs/PRODUCT.md](docs/PRODUCT.md) |
| Read per-feature specs (8 FRs, 6-section shape) | [docs/specs/](docs/specs/) |
| Understand the system design | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Check database DDL + Redis key design | [docs/SCHEMAS.md](docs/SCHEMAS.md) |
| Follow coding rules (API patterns, authz, Kafka conventions) | [docs/CONVENTIONS.md](docs/CONVENTIONS.md) |
| Track phase plan, deliverables, decisions | [docs/ROADMAP.md](docs/ROADMAP.md) |
| AI-assistant working notes for this repo | [CLAUDE.md](CLAUDE.md) |

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Cloud 2025.0
- **Persistence:** MySQL 8 (transactional services), Redis (Matching Service + Gateway rate limiting, planned), MinIO (resume files, planned)
- **Event bus:** Apache Kafka (KRaft mode), `CommonModules` shared event DTOs
- **AI:** swappable `LlmClient` interface (Groq default; OpenAI / Anthropic / Ollama supported)
- **Frontend (Phase 3):** React 19 + React-Bootstrap (Bootstrap 5), Vite, Jest + Testing Library — see [docs/specs/frontend-tech-stack.md](docs/specs/frontend-tech-stack.md)

## Service ports

| Service | Port | Status |
| --- | --- | --- |
| discovery-service (Eureka) | 8761 | built |
| api-gateway | 8080 | built (partial) |
| user-service | 8081 | built (partial) |
| auth-service | 8082 | built |
| job-service | 8083 | planned |
| application-service | 8084 | planned |
| resume-service | 8085 | planned |
| ai-parser-service | 8086 | planned |
| matching-service | 8087 | planned |
