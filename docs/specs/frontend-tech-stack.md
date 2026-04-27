# Frontend Tech Stack (Phase 3)

The frontend is a single-page React app, built once in Phase 3 (see [`../ROADMAP.md`](../ROADMAP.md)). Each FR spec's *Technical Details* section names the specific routes and components that feature contributes; this document captures the cross-cutting choices so they don't repeat in every spec.

## Stack

- **Runtime:** Node 24.14.1 on the dev box.
- **React:** 19.x (current stable, supported on Node 24, supported by React-Bootstrap 2.10+).
- **Component library:** [react-bootstrap](https://react-bootstrap.netlify.app/) 2.10+ on top of [Bootstrap](https://getbootstrap.com/) 5.x. No custom design system; default Bootstrap theme is acceptable for the demo (Phase 3 goal is *"working demo, not pixel-perfect"* per `../ROADMAP.md`).
- **Routing:** React Router 6.x. Protected routes wrapped in a `<RequireAuth>` component that reads JWT presence and redirects to `/login` if missing.
- **HTTP client:** axios with a JWT-injecting request interceptor and the gateway base URL (`http://localhost:8080`). A 401 response interceptor clears `AuthContext` and redirects to `/login`.
- **Forms:** built-in controlled components plus `react-hook-form` for forms with multi-field validation (profile edit FR-2, job create FR-5, apply review FR-8). Bean-Validation-style error messages from the API surface as Bootstrap inline field errors.
- **Auth state:** React Context (`AuthContext`) holding `{ email, role, token }`. Token persisted in `localStorage` for v1 (acknowledged XSS surface; see `FR-1-identity-and-access.md` open questions).
- **Testing:** Jest 29.x + `@testing-library/react` 14+ + `@testing-library/jest-dom` + `@testing-library/user-event`. `jsdom` test environment. No end-to-end browser tests in scope for Phase 3.
- **Build:** Vite (faster + native ESM; create-react-app is no longer maintained). Entry at `frontend/` (sibling to the Java services).

## Cross-cutting UI conventions

These rules apply to every screen unless a feature spec overrides them.

- **Loading state:** Bootstrap `<Spinner>` for short waits; a full-page skeleton/placeholder for first paint of list pages. Resume parsing (FR-4) shows a 30-second-capped progress indicator per NFR-3.3.
- **Error display:** API errors mapped to a Bootstrap `<Alert variant="danger">` at the top of the page; per-field validation errors via `<Form.Control.Feedback>` consuming the `fieldErrors` map from the standard error response shape in `../CONVENTIONS.md`.
- **Confirmation modals:** Bootstrap `<Modal>` for any destructive action — resume FIFO eviction (FR-3.9), resume soft-delete (FR-3.6), application withdraw (FR-8.7), job close (FR-5.3), recruiter status change to terminal states (FR-8.9).
- **Status badges:** Bootstrap `<Badge>` with a consistent colour mapping:
  - Job lifecycle: `DRAFT` = `secondary`, `PUBLISHED` = `success`, `CLOSED` = `dark`.
  - Application lifecycle: `APPLIED`/`REVIEWING` = `info`, `SHORTLISTED` = `primary`, `OFFERED` = `success`, `REJECTED`/`WITHDRAWN` = `secondary`.
  - Match score buckets (FR-7): ≥ 80 = `success`, 50–79 = `warning`, < 50 = `secondary`.
- **Pagination:** Bootstrap `<Pagination>`, defaults `page=0, size=20` to match Spring Data `Pageable` from `../CONVENTIONS.md`.
- **Toasts:** Bootstrap `<Toast>` (top-right, 3-second auto-dismiss) for success acknowledgements ("Application submitted", "Resume saved", "Job published"). Errors stay as alerts, not toasts.
- **Empty states:** every list page (`/matches`, `/jobs`, `/applications/me`, `/saved-jobs`, `/my-jobs`) renders a Bootstrap empty state with one clear next action when the result set is zero.
- **Accessibility:** Bootstrap components are accessible by default; do not add custom interactive controls without an `aria-*` story. Forms use `<Form.Label>` properly associated with `<Form.Control>`.
- **Responsive layout:** Bootstrap grid + breakpoints. Filter sidebars (FR-6) collapse to a Bootstrap `<Offcanvas>` panel below the `md` breakpoint.

## Project layout

```
frontend/
├── src/
│   ├── api/            # axios client + per-resource modules (jobs.ts, resumes.ts, ...)
│   ├── auth/           # AuthContext, RequireAuth, login/register hooks
│   ├── components/     # shared components (StatusBadge, ResumePickerSelect,
│   │                   #                    SkillTagInput, JobResultCard, ...)
│   ├── pages/          # one folder per top-level route
│   │   ├── login/
│   │   ├── register/
│   │   ├── matches/        # FR-7 candidate home
│   │   ├── my-jobs/        # FR-5 recruiter home
│   │   ├── jobs/           # FR-5 + FR-6 (search, detail, new, edit)
│   │   ├── applications/   # FR-8 candidate list
│   │   ├── apply/          # FR-8 /apply/{jobId}
│   │   ├── resumes/        # FR-3
│   │   ├── profile/        # FR-2 view + tabbed edit
│   │   └── saved-jobs/     # FR-6.6/6.7
│   └── App.tsx         # route table
├── public/
├── package.json
└── vite.config.ts
```

## Where each FR spec contributes

| Spec | UI surface |
|---|---|
| [FR-1 — Identity & Access](FR-1-identity-and-access.md) | `/login`, `/register`, `/settings`, `<RequireAuth>`, `AuthContext`, `<NavBar>`, role-aware post-login redirect |
| [FR-2 — Profile Management](FR-2-profile-management.md) | `/profile`, `/profile/edit` (Bootstrap nav-tabs), `<SkillTagInput>`, per-tab Save with dirty-tab confirmation modal |
| [FR-3 — Resume Management](FR-3-resume-management.md) | `/resumes`, `<ResumeUploadButton>`, `<FifoConfirmModal>`, `<ResumePickerSelect>` (reused by FR-4 and FR-8) |
| [FR-4 — AI Resume Parsing](FR-4-ai-resume-parsing.md) | `<AutofillButton>` per profile tab, `<AutofillModal>` (mode picker → spinner → result/error), `<AutofilledFieldBadge>` |
| [FR-5 — Job Postings](FR-5-job-postings.md) | `/my-jobs`, `/jobs/new`, `/jobs/{id}`, `/jobs/{id}/edit`, `<JobFormPage>`, `<StatusBadge>`, `<CloseJobModal>` |
| [FR-6 — Job Search & Discovery](FR-6-job-search.md) | `/jobs`, `/saved-jobs`, `<SearchBar>`, `<FilterSidebar>` (with `<Offcanvas>` mobile variant), `<SortDropdown>`, `<JobResultCard>`, `<BookmarkToggle>` |
| [FR-7 — AI Job Matching](FR-7-ai-job-matching.md) | `/matches` (candidate home), `<MatchCard>`, `<MatchScoreBadge>`, `<MatchExplanation>`, recruiter-side `<ApplicantsRankedView>` with `<SortToggle>` |
| [FR-8 — Applications](FR-8-applications.md) | `/apply/{jobId}` (dedicated route), `<ApplyReviewForm>`, `/applications/me`, `<WithdrawConfirmModal>`, recruiter `<ApplicantsPage>` with `<StatusDropdown>` |

## Out of scope (Phase 3 frontend)

- Server-side rendering, Next.js, or React Server Components — the demo is a plain SPA.
- A custom design system, theming, or dark mode — default Bootstrap theme only.
- End-to-end browser tests (Playwright, Cypress) — Jest + Testing Library only.
- Internationalisation / i18n — strings are in English.
- Service worker / PWA features.
- Real-time updates (WebSocket / SSE) — applications and matches refresh on navigation, not push.

## Open questions

- **JWT storage:** `localStorage` ships in v1 for simplicity. Move to httpOnly cookie with CSRF strategy before any production deploy (Phase 5).
- **State management beyond AuthContext:** v1 uses Context for auth and per-page local state. If a few features need to share more (e.g. saved-jobs cache used by `/jobs` and `/saved-jobs`), introduce a focused context per concern rather than reaching for Redux/Zustand.
- **Form validation library:** `react-hook-form` is chosen for multi-field forms. Whether to add a schema library (Yup / Zod) on top is deferred — start without one and add it if duplication grows.
