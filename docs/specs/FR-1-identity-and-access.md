# FR-1 — Identity & Access

## Goal

Let candidates, recruiters, and admins create accounts, authenticate, and access role-appropriate resources, using a single email-based identity that all microservices recognise without surrogate user IDs.

## Requirements

- (FR-1.1) The system **must** allow a user to register as a candidate (`JOB_SEEKER` role).
- (FR-1.2) The system **must** allow a user to register as a recruiter (`RECRUITER` role).
- (FR-1.3) The system **must** authenticate a user with email + password and return a JWT access token.
- (FR-1.4) The system **must** support log-out via token revocation on the client.
- (FR-1.6) The system **must** allow an authenticated user to change their password.
- (FR-1.9) The system **must** enforce role-based access (`JOB_SEEKER` / `RECRUITER` / `ADMIN`).
- (FR-1.10) The system **must** support soft-deletion of a user account (`is_active = FALSE` in both `auth_db.users` and `userdb.users`).
- (NFR-1.1) All external traffic **must** flow through the API gateway; services are not directly reachable from outside.
- (NFR-1.2) Passwords **must** be hashed with BCrypt at strength ≥ 10.
- (NFR-1.3) Raw passwords **must not** travel over Kafka or appear in logs. The User Service hashes the password and the `UserRegistrationEvent.passwordHash` carries only the hash.
- (NFR-1.4) JWT access tokens **must** be short-lived (≤ 3 hours). Refresh tokens are deferred (FR-1.5).
- (NFR-1.5) The JWT secret **must** be loaded from environment variables. Auth Service and Gateway **must** fail fast at startup outside the `dev` profile if the secret is the dev default, blank, or shorter than 32 bytes.
- (NFR-1.7) Role-based authorization **must** be enforced at the service layer, not only at the gateway.
- (NFR-1.9) The gateway **must** have an explicit CORS configuration. *(open — Phase 0 housekeeping)*
- (NFR-1.10) The system **should** rate-limit `/auth/login` and `/auth/register`. *(open — blocked on Redis being added in Phase 2)*
- (NFR-1.11) Every endpoint **must** validate input; entities **must** never be exposed externally.
- (UI) The frontend **must** expose `/register` and `/login` routes as public, and gate every `/api/**`-driven page behind a `<RequireAuth>` wrapper that redirects to `/login` when no JWT is present.
- (UI) The register form **must** include a role toggle (Candidate / Recruiter) that determines which role goes into `POST /api/users/public/register`.
- (UI) On successful login, candidates **must** be redirected to `/matches` and recruiters to `/my-jobs`. The role for the redirect comes from the JWT claim or the `X-User-Role`-equivalent surfaced in the login response.
- (UI) The top navigation **must** include a logout control that clears stored auth state (JWT in `localStorage`, in-memory `AuthContext`).
- (UI) Password change and account soft-delete **must** be reachable from a settings/profile page; account deletion **must** require a confirmation modal.
- (UI) Forms **must** show inline Bean-Validation errors via Bootstrap `<Form.Control.Feedback>`, mapping the API's `fieldErrors` map from the standard error response shape.

## User Stories

- As a job seeker, I want to register with my email and password so that I can build a profile and apply to jobs.
- As a recruiter, I want to register as a recruiter so that I can post jobs and review applicants.
- As any registered user, I want to log in with my email and password so that I receive a JWT and can use authenticated endpoints.
- As an authenticated user, I want to change my password so that I can rotate credentials if I suspect they have leaked.
- As an authenticated user, I want to soft-delete my account so that my data is hidden across services without requiring an admin.
- As the platform, I want every `/api/**` request to be rejected at the gateway when the JWT is missing or invalid so that downstream services never see unauthenticated traffic.
- As a visitor, I want to land on `/login` when I try to open any authenticated page so that I'm never shown a half-rendered screen with no data.
- As a candidate, I want to be taken to `/matches` (my AI-ranked job feed) right after I log in so that I see relevant jobs without an extra click.
- As a recruiter, I want to be taken to `/my-jobs` right after I log in so that I see the postings I manage without browsing past candidate-oriented pages.
- As any user, I want a visible "Log out" control in the top navigation so that I can end my session without dev-tools tricks.
- As a user changing my password, I want the form to clear and confirm "Password updated" so that I know the action committed.

## Technical Details

- **Owning service(s):** Auth Service `:8082` `[BUILT]`, API Gateway `:8080` `[BUILT, PARTIAL]`, User Service `:8081` `[BUILT, PARTIAL]` (registration entry point and BCrypt origin).
- **Data ownership:**
  - `auth_db.users` — email PK, BCrypt `password`, role enum, `is_active`, timestamps. Indexed on `is_active`.
  - `userdb.users` — email PK, role, profile fields. Created via Kafka event from User Service registration.
- **API surface:**
  - `POST /api/users/public/register` — public, no JWT. Creates `userdb.users` row, hashes password, publishes `UserRegistrationEvent`.
  - `POST /auth/login` — public, no JWT. Verifies BCrypt, returns JWT.
  - `PUT /auth/password` — change password. JWT-required at the gateway; target email is taken from `X-User-Email` so a logged-in user can only change their own password. Body is `{ currentPassword, newPassword }` only.
  - `GET /api/users` — `ADMIN` only.
  - `GET /api/users/me`, `PUT /api/users/me`, `DELETE /api/users/me` — self-targeted (identity from `X-User-Email`).
- **Events produced/consumed:**
  - `user-registration` (User Service → Auth Service): creates `auth_db.users` row, storing the BCrypt hash verbatim.
  - `delete-user` (User Service → Auth Service): sets `auth_db.users.is_active = FALSE`.
- **Cross-service interactions:** JWT issued by Auth Service. Gateway validates HMAC-signed JWT and injects `X-User-Email` and `X-User-Role` headers downstream. Per `CONVENTIONS.md`, services use the self-targeted authz pattern (read identity from `X-User-Email`) for self-only operations and the self-or-admin or role-required patterns for everything else.
- **Status:** `[BUILT]` for FR-1.1, FR-1.3, FR-1.6, FR-1.10, NFR-1.2, NFR-1.3, NFR-1.5, NFR-1.9, NFR-1.11. `[PARTIAL]` for the gateway (no rate limiting until Redis lands in Phase 2; no correlation ID injection yet).
- **Frontend (Phase 3, `[PLANNED]`):**
  - **Stack:** React 19 + React-Bootstrap 2.10+ (Bootstrap 5), React Router 6, axios, `react-hook-form` for the register form, Jest 29 + `@testing-library/react` for tests. Node 24.14.1 dev runtime.
  - **Routes:** `/login` (public), `/register` (public), `/settings` or `/profile/security` for password change and soft-delete (authenticated). Every other authenticated route is wrapped in `<RequireAuth>` (redirects to `/login`).
  - **Key components:** `<LoginForm>`, `<RegisterForm>` (with `<RoleToggle>` between Candidate/Recruiter), `<RequireAuth>`, `<AuthContext>` (holds `{ email, role, token }`), `<NavBar>` (logout button, role-aware menu), `<DeleteAccountModal>`.
  - **Auth state:** JWT in `localStorage` for v1; `AuthContext` rehydrates from storage on app boot and exposes login/logout actions. Axios request interceptor attaches `Authorization: Bearer <token>` to every `/api/**` call.
  - **Post-login redirect:** read role from login response → push `/matches` (JOB_SEEKER) or `/my-jobs` (RECRUITER). ADMIN currently lands on the same default as JOB_SEEKER until FR-1.11 is built.

## Out of Scope

- Refresh tokens (FR-1.5) — deferred to Phase 5. Do not implement here.
- Password reset (FR-1.7) — deferred to Phase 5.
- Email verification (FR-1.8) — deferred to Phase 5. (`is_email_verified` already exists on `userdb.users` but is unused.)
- Admin user management against arbitrary emails (FR-1.11) — deferred to Phase 5. No `{email}`-parameterised admin endpoints in scope.
- Gateway-signed identity headers / HMAC verification (NFR-1.6) — deferred to Phase 5. Today services trust `X-User-Email` and `X-User-Role` without signature checks; mitigated by binding services to localhost in dev.
- Rate limiting on the gateway (NFR-1.10) — blocked on Redis (Phase 2). CORS (NFR-1.9) is now in place via `CorsWebFilter`.
- Profile content (skills/experience/education/preferences/recruiter fields) — see `FR-2-profile-management.md`.

## Edge Cases / Open Questions

- **Edge case:** Eventual consistency between `userdb.users` (created first) and `auth_db.users` (created via Kafka). A user may attempt to log in before the `user-registration` event has been consumed. UI must handle "credentials not yet provisioned" gracefully (NFR-2.4).
- **Edge case:** Gateway header trust. Until NFR-1.6 lands in Phase 5, an attacker who can reach `:8081`/`:8082` directly can spoof `X-User-Email`/`X-User-Role`. Mitigation today: services bind to localhost; only the gateway is exposed.
- **Edge case:** Soft-delete reversal. There is no "undelete" endpoint. If a user re-registers with the same email after soft-delete, decide whether to reactivate the existing rows or reject — current behaviour is unspecified.
- ~~**Open question:** Should `/auth/password` require JWT?~~ Closed — gateway now applies the `JwtAuthentication` filter and the controller derives the target email from `X-User-Email`.
- ~~**Open question:** Hardcoded MySQL credentials in `application.properties`?~~ Closed — both services now read `${DB_USERNAME}` / `${DB_PASSWORD}` (also `DB_HOST`, `DB_PORT`).
- **Open question:** Account locking after N failed login attempts — not in PRODUCT.md. Decide before Phase 5 if this becomes a stretch goal.
- **Edge case (UI):** JWT expires mid-session (NFR-1.4 caps the access token at 3 hours and refresh tokens are deferred). The axios response interceptor **must** detect 401 from the gateway and redirect to `/login`, surfacing a Bootstrap toast "Session expired — please log in again" instead of dropping the user on a blank page.
- **Edge case (UI):** Registration eventual consistency. After a successful register response, an immediate login may fail because the `user-registration` event has not yet been consumed by Auth Service. The login form **should** retry once after a short delay before showing "Credentials not yet provisioned, please try again."
- **Open question (UI):** JWT in `localStorage` is XSS-readable; an httpOnly cookie is safer but requires a CSRF strategy and a server-side session endpoint. v1 ships with `localStorage` for simplicity; revisit before any production deploy (Phase 5).
- **Open question (UI):** Should the role toggle on `/register` be a segmented control or a radio group? Bootstrap supports both; pick during Phase 3 implementation.
