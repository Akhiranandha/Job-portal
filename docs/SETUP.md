# Setup — Local Development

Bring the currently-built services up on a fresh machine. This guide covers what's actually built today: **discovery-service**, **auth-service**, **user-service**, and **api-gateway**, with **Kafka** in Docker. Future services (Job, Resume, Application, AI Parser, Matching) and supporting infra (MinIO, Redis) are documented but not yet runnable — see [ROADMAP.md](ROADMAP.md) for when they land.

If anything below diverges from what's actually in the codebase, file a bug — this doc lags reality unless someone updates it.

## 1. Prerequisites

| Tool | Version | Purpose |
| --- | --- | --- |
| Java JDK | 21 | All services |
| Maven | 3.9+ | Build |
| MySQL Server | 8.x | Transactional storage |
| Docker | recent | Kafka via `docker-compose.yaml` |
| Git | any | Source control |
| Node.js | 24.14.1 | Phase 3 frontend (not built yet) |

Verify:

```bash
java -version          # openjdk 21.x
mvn -version           # Apache Maven 3.9+
mysql --version        # mysql Ver 8.x
docker --version
```

## 2. Clone the repo

```bash
git clone https://github.com/Akhiranandha/Job-portal.git
cd Job-portal
```

The repo layout:

```
.
├── CommonModules/           # shared Kafka event DTOs (mvn install first!)
├── discoveryservice/        # Eureka :8761
├── authservice/             # :8082
├── userservice/             # :8081
├── apigatway/               # API gateway :8080  (note: typo in folder name)
├── docker-compose.yaml      # Kafka + topic init
├── docs/                    # docs (you are here)
└── README.md
```

## 3. MySQL setup

The currently-built services use two databases. Hibernate has `createDatabaseIfNotExist=true` in the connection URL, so they will be auto-created on first run **if** the connecting user has `CREATE` privileges. Easiest path: pre-create them.

Connect as a privileged user:

```bash
mysql -u root -p
```

Create the databases:

```sql
CREATE DATABASE IF NOT EXISTS auth_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS userdb
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Phase 1+ services will additionally need: `resumedb`, `jobdb`, `applicationdb` (and possibly `ai_parserdb`). Create them when those services land — don't pre-create now.

### Credentials

Today `application.properties` in each service has hardcoded `root` / `Kodam@624` credentials. **This is tracked as Phase 0 housekeeping** in [ROADMAP.md](ROADMAP.md) — moving these to env vars is open. Until that's done, either:

- Match those creds locally (`CREATE USER 'root'@'localhost' IDENTIFIED BY 'Kodam@624';` if your `root` is different), **or**
- Edit each service's `application.properties` to point at your local MySQL user.

## 4. Environment variables

Set these in your shell, or use a `.env` file with your IDE's runner.

| Variable | Default | Required outside `dev` profile? | Used by |
| --- | --- | --- | --- |
| `JWT_SECRET` | `defaultSecretKeyForDevelopmentEnvironmentOnly` | **Yes** — must be ≥ 32 bytes and not the dev default | auth-service, api-gateway |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | No | auth-service, user-service |

NFR-1.5 (`@PostConstruct` validator) **fail-fast aborts startup** in auth-service and api-gateway if the JWT secret is the dev default, blank, or shorter than 32 bytes — *unless* the Spring profile is `dev`. For local dev you can either run with `-Dspring.profiles.active=dev` or export a real secret:

```bash
export JWT_SECRET="$(openssl rand -hex 32)"
```

### Future-phase env vars (not used yet, listed for awareness)

- **MinIO (Resume Service, Phase 1):** `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`
- **AI Parser (Phase 2):** `LLM_PROVIDER` (`groq` / `openai` / `anthropic` / `ollama`), `LLM_API_KEY`
- **Redis (Matching Service + Gateway rate limiting, Phase 2):** `REDIS_URL`

## 5. Build order — `CommonModules` first

`CommonModules` is a non-Spring library that holds the Kafka event DTOs in package `com.jobportal.kafka_events`. Auth Service and User Service both depend on it. **It must be installed to your local Maven repo before any consumer service builds**, otherwise you'll see "could not resolve dependency" errors.

```bash
cd CommonModules
mvn clean install
cd ..
```

Repeat this whenever you add or change an event DTO in `CommonModules`.

## 6. Start Kafka

```bash
docker compose up -d
```

What this brings up:

- **kafka** on `localhost:9092` (KRaft mode, single broker).
- **kafka-init** runs once and creates the `user-registration` and `delete-user` topics, then exits. Tail its logs to confirm.

Verify:

```bash
docker compose logs kafka-init
# expect: "Topic user-registration created successfully" and "Topic delete-user created successfully"
```

When Phase 1+ services land, more topics will be added (`job-posted`, `application-submitted`, `profile-updated`, etc. — see [ARCHITECTURE.md](ARCHITECTURE.md) event topology table). Add them to `docker-compose.yaml`'s `kafka-init` command.

## 7. Run the services

Use four terminals (or your IDE's Spring Boot run configurations). Order matters because Eureka must be up before services try to register, and the gateway needs services registered before it can route.

| # | Service | Command | Port |
| --- | --- | --- | --- |
| 1 | discovery-service | `cd discoveryservice && mvn spring-boot:run` | 8761 |
| 2 | auth-service | `cd authservice && mvn spring-boot:run` | 8082 |
| 3 | user-service | `cd userservice && mvn spring-boot:run` | 8081 |
| 4 | api-gateway | `cd apigatway && mvn spring-boot:run` | 8080 |

Auth Service and User Service can be started in either order (or in parallel).

Verify Eureka has registered all four:

```bash
curl http://localhost:8761/eureka/apps
```

You should see four `<application>` entries — `DISCOVERY-SERVICE`, `AUTH-SERVICE`, `USER-SERVICE`, `API-GATEWAY`.

## 8. Sanity check — register, log in, fetch profile

The end-to-end happy path through what's currently built:

### 8a. Register a candidate

```bash
curl -X POST http://localhost:8080/api/users/public/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "Sup3rSecure!",
    "firstName": "Alice",
    "lastName": "Anderson",
    "role": "JOB_SEEKER"
  }'
```

Expected: `200 OK` with `success: true`.

Behind the scenes: User Service writes the row to `userdb.users` with a BCrypt password hash, then publishes a `UserRegistrationEvent` to the `user-registration` Kafka topic. Auth Service consumes the event and creates the matching row in `auth_db.users` (carrying the hash verbatim).

If you log in immediately and get "credentials not yet provisioned," wait a moment and retry — that's the documented eventual consistency between User Service and Auth Service (NFR-2.4).

### 8b. Log in

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "Sup3rSecure!"
  }'
```

Expected: `200 OK` with a JWT in the response. Copy it.

### 8c. Fetch your own profile

```bash
TOKEN="<paste the JWT here>"

curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $TOKEN"
```

Expected: your profile JSON.

If this works, the system is up.

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| auth-service or gateway fails on startup with "JWT secret too short" | Default `JWT_SECRET` outside `dev` profile | Export a real secret (`openssl rand -hex 32`) or run with `-Dspring.profiles.active=dev` |
| Service starts but stays out of Eureka | discovery-service not running, or started after the service | Start discovery-service first; restart the failing service |
| `Could not resolve com.jobportal.kafka_events:CommonModules` | `CommonModules` not `mvn install`-ed | `cd CommonModules && mvn install` |
| Login returns "credentials not yet provisioned" right after register | Eventual consistency — Auth Service hasn't consumed the event yet | Retry after 1–2 seconds. If persistent, check Kafka and `auth-service` logs. |
| `Communications link failure` on startup | MySQL not running or wrong creds | Start MySQL; verify creds match `application.properties` |
| `Address already in use: bind` | Port conflict (8080/8081/8082/8761) | Kill the process holding the port, or change the service's `server.port` |
| Kafka topic missing | `kafka-init` didn't run or failed | `docker compose logs kafka-init`, recreate with `docker compose up -d --force-recreate kafka-init` |
| Gateway returns `401` for `/api/users/me` | Missing or expired JWT | JWT currently expires after 24 hours (NFR-1.4 housekeeping in [ROADMAP.md](ROADMAP.md) — should be 15 min); log in again |

## 10. What this doc does not cover

The remaining infrastructure and services land in later phases. When those land, this doc gets updated.

- **Job / Resume / Application / AI Parser / Matching services** — Phase 1 and Phase 2 work, see [ROADMAP.md](ROADMAP.md).
- **MinIO** (resume file storage) — Phase 1.
- **Redis** (matching ranked lists + gateway rate limiting) — Phase 2.
- **Frontend (`frontend/`)** — Phase 3, see [docs/specs/frontend-tech-stack.md](specs/frontend-tech-stack.md).
- **Observability stack** (OpenTelemetry, Prometheus, Grafana) — Phase 4.
- **CI / Docker images / production deployment** — Phase 4 and Phase 5.
