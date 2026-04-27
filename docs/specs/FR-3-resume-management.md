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

## User Stories

- As a candidate, I want to upload my first resume so that it can be attached to applications and used for AI autofill.
- As a candidate with 5 resumes, I want to upload a sixth and be told which one will be replaced so that I can confirm or cancel before losing my oldest.
- As a candidate, I want to rename "Resume 3" to "Backend - 2025" so that I can recognise it in the application picker.
- As a candidate, I want to download a resume I uploaded earlier so that I can verify what was sent.
- As a candidate, I want to soft-delete an old resume from my list without breaking applications that already used it so that recruiters still see what I applied with.
- As a recruiter, I want to download the resume attached to an application I am reviewing so that I can evaluate the candidate.

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
