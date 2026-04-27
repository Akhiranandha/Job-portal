# FR-4 — AI Resume Parsing (Autofill)

## Goal

Let a candidate parse a resume — either a freshly uploaded file or one already in their resume list — through an LLM and pre-fill the profile edit form with the extracted data, so they can review and save once instead of typing every field by hand.

## Requirements

- (FR-4.1) The profile edit screen **must** offer an "Autofill from resume" action with two modes: pick an existing resume from the user's list, OR upload a new file.
- (FR-4.2) New-file mode **must** trigger two flows sequentially: (a) save to the resume list (with FIFO confirmation if at cap, see `FR-3-resume-management.md` FR-3.9), then (b) parse via LLM.
- (FR-4.3) Existing-resume mode **must** trigger only the parse flow against the chosen resume.
- (FR-4.4) The backend **must** orchestrate both flows behind a single endpoint. The client makes one call and receives `{ resumeId, parsedData }`.
- (FR-4.5) If the save step fails, the parse step **must not** be attempted. An error is returned to the client.
- (FR-4.6) If the save step succeeds but the parse step fails, the response **must** include the `resumeId` and the parse error so the client can offer "retry parsing" against the now-saved resume.
- (FR-4.7) The parsed output **must** include: skills, work experience entries, education entries, summary, and total years of experience.
- (FR-4.8) Parsed data **must** populate the profile form on the client. The profile **must** only be updated when the candidate explicitly saves the form. The system **must not** persist parsed data to `userdb.users` automatically.
- (FR-4.9) The cross-service call from User Service to AI Parser Service **must** use HTTP, not Kafka. Parsing **must** be synchronous from the user's point of view.
- (FR-7.5) Implication for matching: matching uses **profile data only**. Parsed resume data is not persisted as a side channel; it only reaches storage if the candidate saves the form.
- (NFR-2.3) External calls (LLM, MinIO, cross-service HTTP) **must** be wrapped in timeout + retry + circuit breaker (Resilience4j).
- (NFR-3.3) Resume parsing **must** complete within 30 seconds; the user **must** be shown a loading state.
- (NFR-6.6) The exception in `NFR-6.6` (no sync HTTP for cross-service data that can be event-carried) explicitly applies here — sync HTTP is correct for FR-4.9 because the user is waiting.
- (NFR-8.1) Parsing **must** go through an `LlmClient` interface with swappable implementations (Groq default, OpenAI, Anthropic, Ollama), selected by config.
- (UI) Each candidate edit tab on `/profile/edit` whose contents the parser can populate (Skills, Experience, Education, Basic info → summary, years of experience) **must** show an "Autofill from resume" button at the top.
- (UI) Clicking "Autofill from resume" **must** open a Bootstrap `<Modal>` offering two modes: **Pick existing resume** (a `<ResumePickerSelect>` listing the candidate's active resumes; reused from FR-3) and **Upload new file** (a file input that triggers the FIFO confirmation flow from FR-3.9 if the candidate is at cap).
- (UI) During parsing, the modal **must** show a Bootstrap `<Spinner>` and a 30-second-capped progress message ("Parsing resume — this can take up to 30 seconds"). The user **must** see no other interactive controls in the modal except a Cancel button.
- (UI) On success, the modal closes and the form fields populate. Fields touched by parsing **must** be visually marked (e.g. a small "autofilled" Bootstrap `<Badge>`) until the candidate either edits the field or saves the form.
- (UI) On parse failure after a successful save (FR-4.6), the modal **must** show the error and a **Retry parsing** button that re-calls the parse endpoint with `{ resumeId }` (no re-upload).
- (UI) Profile updates from autofill **must** only commit when the candidate clicks the tab's Save button (FR-4.8). The form **must not** auto-submit after autofill.

## User Stories

- As a candidate setting up my profile for the first time, I want to upload my PDF resume and have the form pre-filled so that I don't have to retype every job title and skill.
- As a candidate who already uploaded a resume, I want to parse one of my existing resumes so that I can refresh my profile from a different version without re-uploading.
- As a candidate, I want to edit any parsed field before saving so that I can correct LLM errors (wrong company name, garbled bullets) before they reach my profile.
- As a candidate whose parse failed mid-flow, I want to retry parsing against the resume that was already saved so that I don't have to re-upload the file.
- As a candidate, I want a loading indicator and a 30-second cap so that I know when something is wrong rather than waiting indefinitely.
- As a candidate on the Skills tab, I want an "Autofill from resume" button right at the top of the tab so that I don't have to leave the form to find it.
- As a candidate, I want a clear two-option modal (pick existing vs upload new) so that I don't accidentally re-upload a file I've already stored.
- As a candidate watching the spinner, I want a Cancel button so that I can abort if I changed my mind or another tab needs attention.
- As a candidate whose parse failed but resume was saved, I want a single "Retry parsing" button that doesn't make me re-pick the file so that I'm one click away from a fresh attempt.
- As a candidate seeing autofilled fields, I want a visual marker on each so that I know which to double-check before saving.

## Technical Details

- **Owning service(s):** User Service `:8081` (orchestrates the flow), AI Parser Service `:8086` `[PLANNED]`, Resume Service `:8085` `[PLANNED]`.
- **Data ownership:** AI Parser Service is **stateless v1** — no persistent storage. The future tentative `parse_results` table (cache keyed by `file_hash + llm_provider + llm_model`) is documented in `docs/SCHEMAS.md` but **deferred**.
- **API surface:**
  - User Service: `POST /api/users/profile/parse-resume` `[PLANNED]` — single endpoint covering both modes. Request carries either a multipart file (mode A) or a `{ resumeId }` (mode B). Response: `{ resumeId, parsedData, parseError? }`.
  - AI Parser Service: `POST /parse` `[PLANNED]` — input is either `{ resumeId }` (Parser fetches from MinIO) or `{ fileBytes }`. Output is structured parsed JSON.
  - Resume Service: `POST /resumes` (called by User Service in mode A) — see `FR-3-resume-management.md`.
- **Events produced/consumed:** None. The whole flow is synchronous HTTP from end to end (locked-in decision per `docs/ROADMAP.md`).
- **Cross-service interactions:**
  - **Mode A (upload new file):** Client → User Service → Resume Service `POST /resumes` (sync HTTP, may return 409 with FIFO prompt; client confirms with `confirmEviction=true` and User Service re-calls Resume Service) → AI Parser Service `POST /parse { resumeId }` (sync HTTP) → User Service responds with `{ resumeId, parsedData }`.
  - **Mode B (existing resume):** Client → User Service → AI Parser Service `POST /parse { resumeId }` → User Service responds with `{ resumeId, parsedData }`.
  - AI Parser Service fetches the file from MinIO using the resume's `object_key`.
- **Implementation notes:**
  - PDF text extraction via PDFBox; DOCX via Apache POI.
  - LLM output is JSON-schema enforced.
  - Resilience4j circuit breaker, timeout (≤ 30 s), retry with exponential backoff. Failures return 503 with a retry-able marker.
  - `LlmClient` interface; default impl = Groq (free tier). Config-selected. Vendor-lock-in protection is a `docs/ROADMAP.md` decision-log entry.
- **Status:** `[PLANNED]` — Phase 2 work.
- **Frontend (Phase 3, `[PLANNED]`):**
  - **Stack:** React 19 + React-Bootstrap 2.10+ (Bootstrap 5), axios with a 35-second timeout (a small headroom over the NFR-3.3 30-second budget), Jest 29 + `@testing-library/react`. Mock the parse endpoint with MSW or `jest.fn()` in tests.
  - **Routes:** No new route. The flow is initiated from `/profile/edit` (FR-2) inside a modal.
  - **Key components:** `<AutofillButton>` (per relevant tab), `<AutofillModal>` (mode picker → spinner → result/error), `<AutofillModeChooser>` (pick existing vs upload new), `<ParseSpinner>` (with elapsed-time display capped at 30s), `<RetryParseButton>` (FR-4.6), `<AutofilledFieldBadge>` (small Bootstrap `<Badge>` next to autofilled fields).
  - **State machine inside `<AutofillModal>`:** `idle → choosingMode → uploading | parsing → ready | parseError | uploadError`. The retry path lives in `parseError` and stays inside the modal.
  - **Form integration:** the modal's success callback calls `setValue` on each `react-hook-form`-controlled field and marks the field as `autofilled = true` via the form's metadata; saving the tab clears the marker. No parsed data is auto-saved (FR-4.8).

## Out of Scope

- Asynchronous parsing via Kafka — explicitly rejected (`docs/ROADMAP.md` decision log: "Resume parsing is sync HTTP, not async Kafka. User is waiting; async adds polling for no benefit").
- Caching parse results to avoid re-billing the LLM for identical files — `parse_results` schema is sketched in `docs/SCHEMAS.md` but the decision is deferred.
- Recruiter-side parsing (e.g. parsing job descriptions) — not in PRODUCT.md.
- Automatically writing parsed data into `userdb.users` — explicitly forbidden by FR-4.8 (only saved on explicit form submit).
- Persisting parsed resume data anywhere except in transit to the client — FR-7.5 confirms matching uses profile data only.
- Multi-language resume support — not specified; assume English-only inputs in v1.
- Parsing scanned-image resumes (OCR) — not in scope; extraction relies on PDFBox/POI text layers.

## Edge Cases / Open Questions

- **Edge case:** Parser timeout > 30 s. Resilience4j must trip the circuit breaker; client receives a 503 and shows "parse timed out" with a retry option (FR-4.6 path applies if mode A and the file was already saved).
- **Edge case:** LLM returns malformed JSON. Validate against the expected schema; on failure, return a 502 with a parse error and let FR-4.6 retry against the same `resumeId`. Do not hand the malformed JSON to the client.
- **Edge case:** Skill names returned by the LLM that don't normalise to canonical form (e.g. "Spring Boot" vs "spring-boot"). Decide canonicalisation policy *before* surfacing to the form so Matching Service indexes match downstream.
- **Edge case:** Mode A FIFO confirmation. If the candidate is at 5/5, the first call returns a 409 with FIFO prompt data; the client confirms; the second call carries `confirmEviction=true`. Parse only triggers after a successful save.
- **Edge case:** Save succeeds, parse fails. Response **must** include `resumeId` so the client can offer "retry parsing" against the saved resume (FR-4.6). Don't roll the resume back — the user's storage state is now correct independent of the parse outcome.
- **Edge case:** AI Parser is unreachable while Resume Service is up. Mode A still completes the save and returns parse error; mode B returns parse error with no resume side effect.
- **Edge case:** Very large PDF / DOCX (close to 5 MB cap). PDFBox extraction can be slow; combine with the 30 s NFR-3.3 budget, the LLM call may be the dominant cost — parser must monitor end-to-end latency, not just LLM call latency.
- **Open question:** Should User Service expose the parse error to the client verbatim, or sanitise it? LLM provider errors may include provider-identifying strings; portability (NFR-8.1) suggests sanitising.
- **Open question:** Caching at AI Parser by `(file_hash, model)` — deferred per `docs/SCHEMAS.md`. Decide before Phase 2 ships if LLM costs become material.
- **Edge case (UI):** The user navigates away from `/profile/edit` while parsing is in flight. Either (a) cancel the in-flight request via `AbortController`, or (b) leave it running and discard the response. v1 should pick (a); document the choice in the component.
- **Edge case (UI):** Parsed Skills include items already in the candidate's profile. Merge by deduping on canonical (lowercase, trimmed) skill name; keep the candidate's existing chip rather than the parser's casing.
- **Edge case (UI):** Parsed Experience entries with malformed dates. Show the row in the form anyway, with a Bootstrap inline error on the date field, so the candidate can correct rather than silently lose the entry.
- **Open question (UI):** Should "autofilled" markers persist across page reloads, or only within the current edit session? Persisting requires server-side tracking; for v1, in-session is sufficient.
- **Open question (UI):** Cancel during parse — does that abort the LLM call server-side too? If the AI Parser does not support request cancellation, "Cancel" only stops the UI from waiting; the call continues to billing. Confirm Resilience4j cancel behaviour during Phase 2.
