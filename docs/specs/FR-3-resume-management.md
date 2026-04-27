# FR-3 — Resume Management (Multi-Resume, Cap 5)

## Goal

Let candidates upload, label, list, download, and soft-delete up to five resumes (PDF/DOCX) so they can attach a chosen version to each job application and feed any of them into AI parsing for autofill.

## Requirements

- (FR-3.1) A candidate **must** be able to have up to 5 active resumes. Files **must** be PDF or DOCX, ≤ 5 MB each.
- (FR-3.2) Each resume **must** be auto-labeled `Resume N` using a per-user counter that always increments. Deletion **must not** reuse numbers.
- (FR-3.3) A candidate **must** be able to rename a resume label, max 50 characters.
- (FR-3.4) A candidate **must** be able to view their list of resumes, showing label and upload date, sorted newest first.
- (FR-3.5) A candidate **must** be able to download any of their own resumes.
- (FR-3.6) A candidate **must** be able to soft-delete a resume. If the resume is attached to one or more applications, the file **must** be retained so recruiters can still download it.
- (FR-3.7) The system **must** validate file type (PDF/DOCX) and size (≤ 5 MB) on upload.
- (FR-3.8) A recruiter **must** be able to download the resume attached to an application they own (see `FR-8-applications.md`).
- (FR-3.9) When a candidate is at the 5/5 cap and uploads a new resume, the system **must** show a FIFO confirmation prompt: *"Your oldest resume (Resume X, uploaded [date]) will be soft-deleted. Continue?"*. On confirm, the oldest active resume **must** be soft-deleted and the new resume saved.
- (NFR-1.12) File uploads **must** be size-capped, type-checked, and stored under server-generated keys, never user-supplied filenames.
- (NFR-3.5) The list endpoint **must** be paginated.
- (NFR-5.4) Resume files **must** be stored in object storage (MinIO).
- (NFR-8.2) Storage **must** be S3-compatible so MinIO locally and an S3-compatible store in prod are both viable.
- (UI) The `/resumes` route **must** show the candidate's active resumes (newest first), each row showing label, original filename, upload date, file size, and Rename / Download / Delete actions.
- (UI) The upload control **must** be a Bootstrap file input that accepts only `.pdf` and `.docx` (`accept` attribute) and surfaces client-side size validation (≤ 5 MB) before hitting the API. The API remains the source of truth for validation per NFR-1.12.
- (UI) When the candidate is at 5/5 active resumes, the upload **must** trigger a Bootstrap `<Modal>` showing the FIFO confirmation message from FR-3.9 with the oldest resume's label and upload date. **Confirm** triggers the upload with `confirmEviction=true`; **Cancel** aborts without an upload.
- (UI) Rename **must** open an inline editable label or a small modal capped at 50 characters, with character-count feedback.
- (UI) Soft-delete **must** prompt a Bootstrap modal "Delete Resume X? Applications already submitted with this resume will keep working." Confirm calls `DELETE /api/resumes/{id}`.
- (UI) Upload progress **must** surface via a Bootstrap `<ProgressBar>` for files larger than ~1 MB; smaller files can show a spinner.

## User Stories

- As a candidate, I want to upload my first resume so that it can be attached to applications and used for AI autofill.
- As a candidate with 5 resumes, I want to upload a sixth and be told which one will be replaced so that I can confirm or cancel before losing my oldest.
- As a candidate, I want to rename "Resume 3" to "Backend - 2025" so that I can recognise it in the application picker.
- As a candidate, I want to download a resume I uploaded earlier so that I can verify what was sent.
- As a candidate, I want to soft-delete an old resume from my list without breaking applications that already used it so that recruiters still see what I applied with.
- As a recruiter, I want to download the resume attached to an application I am reviewing so that I can evaluate the candidate.
- As a candidate on `/resumes`, I want a clearly labeled "Upload resume" button that's disabled (or shows "5/5 — replace oldest?") when I'm at cap so that the FIFO behaviour isn't a surprise.
- As a candidate uploading a sixth resume, I want the FIFO modal to name exactly which file ("Resume 2, uploaded 15 Apr 2026") will be replaced so that I confirm with full information.
- As a candidate, I want immediate inline error feedback when I pick a `.zip` or a 7 MB PDF so that I don't wait for an API round-trip to learn it's invalid.
- As a candidate renaming a resume, I want the 50-char limit visible while I type so that I don't have to guess where to truncate.
- As a candidate deleting a resume that's attached to an application, I want the modal copy to reassure me that recruiters can still download it so that I don't worry about retracting submissions.

## Technical Details

- **Owning service(s):** Resume Service `:8085` `[PLANNED]` for Phase 1.
- **Data ownership:**
  - `resumedb.resumes` — UUID `id` PK, `user_email`, `label` (≤ 50 chars), original `file_name`, `object_key` (server-generated UUID-based MinIO key, e.g. `resumes/{userEmail}/{uuid}.pdf`), `file_size_bytes`, `content_type` (`application/pdf` or `application/vnd.openxmlformats-officedocument.wordprocessingml.document`), `is_active`, timestamps. Indexes: `(user_email, is_active)`, `(user_email, created_at)`.
  - `resumedb.resume_counters` — `user_email` PK, `next_number INT` (default 1). Manipulated inside the resume-insert transaction with `SELECT ... FOR UPDATE` to guarantee monotonic, never-reused numbering.
  - MinIO bucket `resumes` for the file bytes. Object key is server-generated UUID; never trust the user-supplied filename for the storage path.
- **API surface:**
  - `POST /api/resumes` — multipart upload. Returns 409 with FIFO prompt data if at cap (5/5 active); client confirms by re-calling with `confirmEviction=true`.
  - `GET /api/resumes/me` — list active resumes for the caller, newest first, paginated.
  - `GET /api/resumes/{id}/download` — stream the file.
  - `PATCH /api/resumes/{id}` — rename label.
  - `DELETE /api/resumes/{id}` — soft delete (sets `is_active = FALSE`); MinIO object is retained.
  - `GET /api/resumes/{id}/cap-status` — used by the FIFO prompt UX.
  - All endpoints require JWT and the self-targeted authz pattern; identity from `X-User-Email`.
- **Events produced/consumed:** None in scope. Resume Service does not publish or consume Kafka events for v1 — applications reference `resumeId` directly via sync HTTP / event-carried state on the Application Service side.
- **Cross-service interactions:**
  - Called by User Service during AI autofill (mode A — "upload new file"; see `FR-4-ai-resume-parsing.md`).
  - Called by AI Parser Service to fetch file bytes for a given `resumeId`.
  - Called by Application Service or recruiter UI for download.
- **Status:** `[PLANNED]` — entire service is Phase 1 work.
- **Frontend (Phase 3, `[PLANNED]`):**
  - **Stack:** React 19 + React-Bootstrap 2.10+ (Bootstrap 5), axios with `multipart/form-data` for uploads + `XMLHttpRequest`/axios `onUploadProgress` for the progress bar, Jest 29 + `@testing-library/react`.
  - **Routes:** `/resumes` (list + upload), accessible only to candidates.
  - **Key components:** `<ResumeListPage>` (loads `GET /api/resumes/me`), `<ResumeRow>`, `<ResumeUploadButton>` (file input + cap check + `POST /api/resumes`), `<FifoConfirmModal>` (renders the API's 409 prompt payload), `<RenameLabelModal>`, `<DeleteResumeModal>`, `<UploadProgressBar>`.
  - **Resume picker reuse:** the same `<ResumePickerSelect>` (Bootstrap `<Form.Select>` showing label + upload date) is reused inside the apply flow (FR-8) and the AI parsing mode picker (FR-4 mode B).
  - **Download:** anchor with `download` attribute pointing at `GET /api/resumes/{id}/download`. The axios interceptor will not attach to plain `<a>` clicks; either issue a fetch + blob download programmatically (cleaner with auth) or rely on the browser's `Authorization` header for the same-origin gateway.

## Out of Scope

- Parsing resume content into structured data — see `FR-4-ai-resume-parsing.md`.
- Recruiter access to candidate resumes that are *not* attached to one of their applications — out of scope; only via Application download (FR-3.8).
- In-browser resume preview (PDF.js, etc.) — not in PRODUCT.md.
- Versioning a single resume (replace contents while keeping the same `id`) — not in scope; "newer resume" means a new row with a new `id`.
- File-format conversion (e.g. PDF → DOCX) — not in scope.
- Caching parsed results keyed by file hash — see `docs/SCHEMAS.md` *Open questions*; deferred.

## Edge Cases / Open Questions

- **Edge case:** Counter never reuses numbers. If a candidate has Resume 1, 2, 3 and deletes 2, the next upload is `Resume 4`, not `Resume 2`. The counter row persists even when all resumes are soft-deleted; first upload after a full clear is `Resume {nextNumber}`, not `Resume 1`. This is intentional per FR-3.2.
- **Edge case:** FIFO eviction race. If two upload requests at cap arrive concurrently, both could read 5 active resumes and both could try to evict the same oldest one. Implement using a `SELECT ... FOR UPDATE` on the candidate's resume rows or a unique-index trick to serialise evictions.
- **Edge case:** Orphaned MinIO objects on failed insert. Upload to MinIO first, then DB insert — if DB insert fails, schedule MinIO object cleanup (or upload-then-commit pattern). Avoid leaking storage on failed transactions.
- **Edge case:** Soft-deleted resume referenced by an active application. `is_active = FALSE` removes from the candidate's list (FR-3.6) but the row and MinIO object stay. Recruiter download (FR-8.12) **must** target the specific `resume_id` regardless of `is_active`.
- **Edge case:** MIME type spoofing. Validate by inspecting magic bytes, not just `Content-Type` header (NFR-1.12).
- **Edge case:** Filename Unicode / very long names. Stored `file_name` is for display only; storage key is the server UUID, so unsafe characters cannot affect the file system.
- **Open question:** What happens on download for an `is_active = FALSE` resume that has zero active applications? Two options: 404 (treat as fully deleted) or 200 (allow recruiter who has a stored `resumeId` reference). Decide during Phase 1 implementation.
- **Open question:** Should label rename be allowed on a soft-deleted resume? Probably no, but unspecified.
- **Edge case (UI):** Authenticated download via `<a href>`. A plain anchor cannot carry the `Authorization: Bearer` header. The downloader **must** either fetch the file as a blob and trigger `URL.createObjectURL` + programmatic click, or the gateway **must** support a short-lived signed URL endpoint. Decide during Phase 3.
- **Edge case (UI):** FIFO modal copy and the 409 response payload must agree on which resume is "oldest." If the backend changes the eviction order in a future release (e.g. switches to LRU), the modal copy must come from the API response, not be computed client-side.
- **Edge case (UI):** Drag-and-drop. Bootstrap doesn't ship a drop-zone component; if added, it must still respect the same client-side type/size validation as the file input.
- **Open question (UI):** Should the resume list be paginated? At a hard cap of 5 active resumes per user (FR-3.1), pagination is overkill — render all rows. Soft-deleted ones are excluded. Confirm before Phase 3 implementation.
