# Coding Conventions — Job Portal

Project-specific rules. Standard Spring Boot conventions apply
elsewhere.

---

## Identifiers

- **Email is the user ID** across all services. Do not introduce
  surrogate `userId` columns or DTOs unless explicitly extending the
  identity model.
- **Job, application, resume IDs:** UUID strings, generated server-side.
  Never expose database auto-increment IDs externally.

---

## API conventions

### Request paths
- `/auth/**` — auth-service public endpoints
- `/api/users/public/**` — open user-service endpoints (registration), no JWT
- `/api/users/**` — user-service, JWT required
- `/api/jobs/**` — job-service, JWT required
- `/api/applications/**` — application-service, JWT required
- `/api/resumes/**` — resume-service, JWT required

Routes are evaluated in declaration order; place the more specific
public path before the JWT-filtered one in `application.properties`.

### DTOs
- Never expose JPA entities on the API boundary.
- Request DTOs end in `Request` (e.g. `UserUpdateRequest`).
- Response DTOs end in `Response` (e.g. `UserResponse`).
- Use Bean Validation annotations (`@NotNull`, `@Email`, `@Size`,
  `@Past`) on all request DTOs.

### Mapping
- **User Service uses ModelMapper.** Strict matching, null-skipping.
- **Auth Service uses manual builder mapping.**
- **Do not mix styles within a service.** New services should pick
  one and document it in their service spec.

### Response wrappers
- **User Service** wraps responses in `ApiResponse<T> = { success,
  message, data, timestamp }`.
- **Auth Service** returns payloads directly (no wrapper).
- **New services should use the wrapper** for consistency
  (NFR-6.2). Auth Service stays as-is to avoid breaking Gateway
  integration.

### Error response shape (standard)
```json
{
  "success": false,
  "message": "Human-readable message",
  "error": "ERROR_CODE",
  "fieldErrors": { "fieldName": "what's wrong" },
  "timestamp": "2026-04-25T10:30:00Z"
}
```

- Use a `@RestControllerAdvice` GlobalExceptionHandler in every service.
- Custom exceptions extend a service-specific base
  (e.g. `UserServiceException extends RuntimeException`).
- Map exceptions to appropriate HTTP statuses:
  - 400 — validation, bad input
  - 401 — auth failure
  - 403 — authz failure
  - 404 — not found
  - 409 — conflict (duplicate, at-cap, etc.)
  - 500 — anything unexpected

### Pagination
- All list endpoints accept `Pageable` (Spring Data) with sensible
  defaults (page=0, size=20, max=100).
- Response includes total count, page number, page size, total pages.

---

## Authorization patterns

### Self-targeted (preferred for self-only operations)
Don't take a target identifier in the path; use the
gateway-injected `X-User-Email` header as the only source of truth.
The endpoint reads as `/api/<resource>/me`.

```java
@GetMapping("/me")
public ResponseEntity<...> getCurrentUser(
        @RequestHeader(value = "X-User-Email", required = false) String requesterEmail) {
    if (requesterEmail == null || requesterEmail.isBlank()) {
        throw new ForbiddenException("Authentication context missing");
    }
    // ... use requesterEmail as the target
}
```

This makes the "user A modifies user B" attack unrepresentable in
the URL space — there's no path parameter to tamper with.

### Self-or-admin (when admin must operate on a target user)
Use only for endpoints where an admin legitimately needs to act on
someone else's resource (e.g. FR-1.11 admin user management).

```java
String requesterEmail = request.getHeader("X-User-Email");
String requesterRole  = request.getHeader("X-User-Role");

if (!requesterEmail.equalsIgnoreCase(targetEmail)
        && !"ADMIN".equals(requesterRole)) {
    throw new ForbiddenException("Cannot modify another user's profile");
}
```

### Role-required (recruiter-only actions)
```java
if (!"RECRUITER".equals(requesterRole)) {
    throw new ForbiddenException("Recruiter role required");
}
```

### Owner-only (recruiter editing their own job)
```java
Job job = repo.findById(jobId).orElseThrow();
if (!job.getRecruiterEmail().equals(requesterEmail)) {
    throw new ForbiddenException("Not the owner of this job");
}
```

---

## Kafka conventions

- All event DTOs live in `CommonModules`, package
  `com.jobportal.kafka_events`. Run `mvn install` on CommonModules
  after adding new events.
- Topic names: `kebab-case` (e.g. `user-registration`, `job-posted`).
- Event class names: `*Event` suffix (e.g. `UserRegistrationEvent`).
- Use `key = email` for user-related events (preserves ordering per user).
- Producers: idempotence enabled, `acks=all`.
- Consumers: explicit `group.id` per service, `trusted.packages`
  restricted to `com.jobportal.kafka_events`.
- Error handling: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`,
  `FixedBackOff(1s, 3 retries)`, then DLT.
- All consumers must be **idempotent**. Dedupe on natural key
  (email, applicationId, etc.) before mutating state.

---

## Database conventions

- **Database per service.** No service reads another service's DB.
- `ddl-auto=update` is OK in dev. Production migrations are TBD
  (Flyway or Liquibase, decision deferred).
- Soft delete pattern: `isActive BOOLEAN`, default `true`. Repositories
  filter `isActive = true` by default; expose explicit methods for
  including inactive rows.
- Timestamps: `createdAt`, `updatedAt` via `@PrePersist`, `@PreUpdate`.
- Don't put behavior on entities. Keep them dumb data carriers.

---

## Logging

- Use SLF4J. No `System.out.println`.
- Structured logging (target NFR-4.1): include `correlationId`,
  `userEmail` (when available), `service` on every line.
- Never log: passwords, tokens, full Authorization headers, raw
  request bodies that may contain credentials.
- Log levels:
  - `ERROR` — unrecoverable failure, action needed
  - `WARN` — recoverable failure, retry expected to succeed
  - `INFO` — significant business events (login, registration, apply)
  - `DEBUG` — flow tracing during dev

---

## Testing

- Unit tests for service layer (mock the repository).
- Integration tests use Testcontainers for MySQL and Kafka.
- Don't test Spring framework code (don't test that `@Autowired` works).
- Test data: builders or factories, not raw entity construction with
  20 setters in the test.

---

## Out-of-scope rules (don't add without asking)

- No GraphQL. REST only.
- No reactive programming in services (only Gateway is WebFlux).
- No Lombok-only entities — explicit getters/setters preferred for
  clarity in DTOs. Lombok `@Data` is fine on entities.
- No custom security frameworks. Use Spring Security or write the
  filter cleanly.
- No business logic in controllers. Service layer or domain object.
