# Feature 3.1: Document Upload & Storage

> **Epic**: 3 — Document Processing & Parsing (SCRUM-34)
> **Sprint**: 3–4 (2026-10-02 → 2026-10-11)
> **Points**: 16 (three stories)
> **Status**: Approved 2026-10-02
> **Author**: Stefan Kostyk
> **Governing docs**: roadmap § Feature 3.1, US-003 (certificate photo, DoD "security review for file upload"), DATA_DICTIONARY §2.3 and appendix (constraints), V006, V013, state-machine-document.puml, RBAC_matrix.md ("Upload Scanned Document"), AUTH §3.3, SECURITY_ARCHITECTURE (encryption at rest, OWASP A05), THREAT_MODEL, PRIVACY_BY_DESIGN (export, erasure), ADR-014, ADR-021, openapi.yml `/awards/{id}/documents`, `/documents/{id}`, EPIC-03 tracker

## 1. Problem and personas

An award without its certificate is only a claim: the faculty secretary who reviews it asks the employee for a scan by e-mail, and the scan then lives in a mailbox, outside the system and outside the GDPR export. US-003 asks that the employee can photograph the certificate from her phone while filling in the award. This feature stores the certificate and supporting files with the award, in private object storage, readable by exactly the people who may read the award.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | Attach the certificate (a phone photo or a PDF) to her draft, see what is attached, remove a wrong file before submitting |
| Alina, faculty secretary; Prof. Martynyuk, dean | Open the certificate of a submitted award of their unit without asking for it |
| Dmytro, GDPR officer | Uploaded personal documents are encrypted at rest, every download is recorded, the owner's export links to her files |

## 2. Scope

**In (this feature)**

- Object storage through the S3 API (MinIO in every environment, ADR-021): one private bucket, objects keyed by award and a random id, encrypted at rest
- `POST /api/v1/awards/{id}/documents`, `GET /api/v1/awards/{id}/documents`, `GET /api/v1/documents/{id}`, `DELETE /api/v1/documents/{id}`
- Checks: size, file type by content (not by name or the browser's header), extension against content, duplicate file on the same award, at most 10 documents per award
- Document type (certificate, diploma, supporting document, photo) and an optional description (new migration V025)
- Downloads through the API only, authorised by the award read rule and recorded in `audit_logs`
- Objects removed when their document or draft is deleted; a daily sweep removes orphaned objects
- Upload in the award form (drag and drop, file picker, phone camera) and the document list on the award page
- Malware scanning with ClamAV before a file is stored

**Out (where it goes)**

- OCR, field extraction, confidence scores, `processing_status` changes — Features 3.2 and 3.3 (deferred until after Epic 8); every upload stays `PENDING`
- Adding documents to a returned request during resubmission (`documents.request_id`) — Epic 4
- `awards.verification_badge` set by a reviewer who checked the certificate — Epic 4
- Erasure of documents of submitted awards and of an erased account — Epic 6 (with 2.4.2)
- Document rows in the per-award audit trail tab — Epic 6 audit search (the rows exist from this feature on)
- Image compression or rotation in the browser — not needed at 10 MB (phone photos are 2–8 MB)
- Per-file encryption keys with monthly rotation (SECURITY_ARCHITECTURE) — replaced by MinIO server-side encryption (deviation 6)

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-35 (#111) | 3.1.1 Document storage and upload API | 8 | no | 2.1.1 (merged); 3.0.1 for the production limits |
| SCRUM-36 (#112) | 3.1.2 Certificate upload in the award form and award page | 5 | no | 3.1.1 |
| SCRUM-37 (#113) | 3.1.3 Malware scanning of uploads | 3 | no | 3.1.1 |

3.1.2 and 3.1.3 are independent of each other; 3.1.3 adds one error code that 3.1.2 shows (AC-2.5 lists it; whichever story lands second wires it).

## 4. Acceptance criteria

### 3.1.1 Document storage and upload API (SCRUM-35)

- **AC-1.1** Given the owner of a `DRAFT` award, when she posts `multipart/form-data` with `file`, `type` and an optional `description` to `/api/v1/awards/{id}/documents`, then 201 with `Document` (`id`, `awardId`, `fileName`, `type`, `mimeType`, `size`, `description`, `uploadedAt`, `uploadedBy`) and a `Location` header; the row has `storage_bucket`, `storage_key` = `awards/<awardId>/<random UUID>`, `checksum_sha256`, `processing_status` `PENDING`, `storage_url` null; the stored object has the uploaded bytes.
- **AC-1.2** Given an empty file, then 400 `EMPTY_FILE`; given a file over 10 485 760 bytes, then 413 `FILE_TOO_LARGE`; given a request body over the multipart limit, then 413 problem details, never 500. Nothing is stored in any case.
- **AC-1.3** Given content that is not PDF, JPEG, PNG or WEBP by its leading bytes, then 400 `UNSUPPORTED_TYPE`; given an extension of another of these types than the content (`scan.pdf` holding a PNG), then 400 `CONTENT_MISMATCH`; given a missing or unknown extension and a supported content, then the extension of the content is appended. The stored `mime_type` and `file_type` come from the content, never from the request's `Content-Type`.
- **AC-1.4** Given someone else's award or an unknown id, then 404; given the owner's award that is not a draft, then 409 `AWARD_NOT_EDITABLE`; given a caller without `award:update:own`, then 403; given a non-numeric id, then 400.
- **AC-1.5** Given an award with 10 documents, then the next upload answers 409 `DOCUMENT_LIMIT`; given a file with the checksum of a document already on the award, then 409 `DUPLICATE_DOCUMENT` with the existing document's id.
- **AC-1.6** Given a file name with a path (`C:\scans\..\диплом.pdf`), control characters or more than 255 characters, then `fileName` keeps the last path segment without control characters, Unicode letters kept, shortened to 255 with its extension kept; a missing `type` or one outside `CERTIFICATE`, `DIPLOMA`, `SUPPORTING_DOCUMENT`, `PHOTO` → 400; a description over 500 characters → 400.
- **AC-1.7** Given a caller who may read the award (the owner in any status; a holder of an `award:read:*` scope covering the award's organisation for non-drafts — `AwardOwnership.isReadable`), when she calls `GET /api/v1/awards/{id}/documents`, then 200 with the documents oldest first; anyone else, or an unknown award, gets 404.
- **AC-1.8** Given the same read rule, when she calls `GET /api/v1/documents/{id}`, then 200 with the bytes streamed from storage, `Content-Type` from the row, `Content-Disposition: attachment; filename*=UTF-8''<encoded name>`, `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-store` and `Content-Security-Policy: sandbox`; an `audit_logs` row `DOCUMENT_DOWNLOAD` with the document and award id and the caller is written. A document of an award she may not read answers 404.
- **AC-1.9** Given the owner of a draft, when she calls `DELETE /api/v1/documents/{id}`, then 204, the row is gone and the object is removed after the commit; given a non-draft award, then 409 `AWARD_NOT_EDITABLE`; given anyone else, then 404.
- **AC-1.10** Given a draft with documents, when the owner deletes the draft, then every object of its documents is removed after the commit.
- **AC-1.11** Given object storage that is unreachable, then an upload answers 503 `STORAGE_UNAVAILABLE` and no row is written; given a database failure after the object was written, then the object is removed; given objects under `awards/` with no row and older than 24 h, then the daily sweep (`app.documents.sweep-cron`) removes them and logs the count.
- **AC-1.12** Given application start, then the bucket (`app.documents.bucket`, default `award-documents`) is created when missing and has no anonymous access policy; given the Compose environments, then MinIO encrypts objects at rest (`MINIO_KMS_SECRET_KEY`, auto-encryption on) and MinIO publishes no port in production.
- **AC-1.13** Given the owner's GDPR export (1.3.3), then each document's `api_path` (`/api/v1/documents/{id}`) downloads the file for her.
- **AC-1.14** Given an upload or a deletion, then `trg_documents_audit` writes the `INSERT`/`DELETE` row with the caller as actor (Feature 2.2 D-3).
- **AC-1.15** Given the functional test run, then a 10 MB upload answers in under 5 s (BRD §5 "Document Upload").
- **AC-1.16** Given the frontend nginx and the production proxy, then a 10 MB upload passes both (`client_max_body_size`, Spring `max-file-size` 10 MB and `max-request-size` 11 MB).

### 3.1.2 Certificate upload in the award form and award page (SCRUM-36)

- **AC-2.1** Given the award form (new award or draft), then a «Документи» section shows a drop zone «Перетягніть файли сюди або оберіть файл», a «Сфотографувати» button (file input with `capture="environment"`, shown on touch devices), a type select (default «Сертифікат» for the first file, «Додатковий документ» afterwards) and the hint «PDF, JPG, PNG або WEBP, до 10 МБ, не більше 10 файлів».
- **AC-2.2** Given a new award that was never saved, when the owner adds a file, then the draft is saved first exactly as «Зберегти чернетку» does, the address changes to the draft's edit route, and the upload follows; when saving fails, the file stays queued with the save error.
- **AC-2.3** Given a file of another type or over 10 MB, or more files than the remaining slots, then the file is refused in the browser with its reason and no request is sent.
- **AC-2.4** Given several files, then they upload one after another, each with a progress bar and its name; the section announces «Завантажено: <name>» through `aria-live`.
- **AC-2.5** Given a server refusal, then the file's row shows the reason: `FILE_TOO_LARGE` (413) «Файл більший за 10 МБ», `UNSUPPORTED_TYPE` «Непідтримуваний формат файлу», `CONTENT_MISMATCH` «Вміст файлу не відповідає його розширенню», `DUPLICATE_DOCUMENT` «Цей файл уже додано», `DOCUMENT_LIMIT` «Досягнуто ліміту в 10 файлів», `MALWARE_DETECTED` «Файл містить шкідливий код і не був завантажений», `STORAGE_UNAVAILABLE`, `SCANNER_UNAVAILABLE` or a network error «Не вдалося завантажити файл» with «Спробувати ще раз»; refusals that a retry cannot fix offer only «Прибрати».
- **AC-2.6** Given uploaded documents, then the list shows name, type, size (КБ/МБ), Kyiv date; «Завантажити» saves the file under its name; an image opens in a preview dialog; on a draft of the owner «Видалити» asks «Видалити документ «<name>»?» and removes it.
- **AC-2.7** Given the award page of any award the caller may read, then a «Документи» section lists the documents with download and preview; delete appears only to the owner of a draft; with none, «Документи не додано».
- **AC-2.8** Given a draft without documents, when the owner submits it, then the confirmation adds «Ви не додали жодного документа. Рецензент може попросити копію нагороди.» and submission stays possible.
- **AC-2.9** Given a download, then the browser fetches it with the access token (no token in the URL) and saves it from a blob; a 404 shows «Документ більше не доступний» and reloads the list.
- **AC-2.10** Given the UI in English, then all texts of the section, the errors and the dialogs are in English.
- **AC-2.11** Given keyboard use, then the drop zone opens the file picker with Enter or Space, every row action is reachable by Tab, and the section passes the axe check of the E2E run.

### 3.1.3 Malware scanning of uploads (SCRUM-37)

- **AC-3.1** Given an upload that passed the size check, then it is scanned by `clamd` (INSTREAM over TCP) before the type check and before anything is stored; given the EICAR test file under any name, then 422 `MALWARE_DETECTED`, nothing stored, an `audit_logs` row `DOCUMENT_REJECTED` with the signature name, the award id and the caller, and a `WARN` log line without the file name.
- **AC-3.2** Given `clamd` unreachable or not answering within `app.documents.scan.timeout` (default 30 s), then 503 `SCANNER_UNAVAILABLE` and nothing stored (fail closed); the actuator health shows `clamav` down.
- **AC-3.3** Given both Compose files, then a `clamav` service with a health check and signature updates runs, the backend depends on it being healthy, and it publishes no port in production; `app.documents.scan.enabled` is true everywhere except tests that do not exercise scanning.
- **AC-3.4** Given `.\tools\e2e.ps1`, then the scanner is started with the infrastructure and the E2E run includes an EICAR upload.

## 5. Edge cases

| Case | Expected |
|------|----------|
| Two uploads of the same file to one award at the same time | One 201, one 409 `DUPLICATE_DOCUMENT` (the duplicate check runs under the award's row lock) |
| Eleventh upload racing the tenth | The count is checked under the award's row lock; one of them answers 409 `DOCUMENT_LIMIT` |
| Draft submitted while an upload is in flight | Upload and submission both lock the award row; the upload that commits after the submission answers 409 `AWARD_NOT_EDITABLE` and its object is removed |
| Draft deleted while its document is downloading | The stream that started finishes; the next request answers 404 |
| Upload of a phone photo named `IMG_2041.HEIC` | 400 `UNSUPPORTED_TYPE`; the UI refuses it before upload (AC-2.3); camera capture on iOS delivers JPEG |
| PDF with embedded JavaScript | Stored; it is only ever served as an attachment with `Content-Security-Policy: sandbox` and `nosniff` |
| File name `..`, empty or only control characters | Stored as `document.<ext>` |
| Same file on two different awards | Allowed; the duplicate rule is per award |
| Owner moves to another faculty after submission | Readers follow the award's organisation (Feature 2.1), not the owner's |
| Reader loses her scope while the award page is open | The next list or download answers 404; the UI shows «Документ більше не доступний» |
| Access token expires during a long upload | The upload already sent completes; the next request refreshes the token through the interceptor |
| MinIO restarted between upload and download | Object kept on the volume; download works |
| Object missing for an existing row (manual deletion in MinIO) | Download answers 404 `DOCUMENT_CONTENT_MISSING`, logged as `ERROR` |
| Network drop in the middle of a multipart upload | Spring discards the partial request; nothing stored; the UI offers «Спробувати ще раз» |
| GDPR export link opened in a browser tab directly | 401 (no bearer token); the export page explains that files are downloaded from the award page — unchanged from 1.3.3 |

## 6. Dependencies

### Tables

| Table | Change |
|-------|--------|
| `documents` (V025) | New `document_type VARCHAR(30) NOT NULL DEFAULT 'SUPPORTING_DOCUMENT'` with `ck_documents_document_type`, `description VARCHAR(500)`; new unique index `uq_documents_award_checksum` on `(award_id, checksum_sha256)`. No existing rows in any environment. |
| `awards` | No change; its row lock serialises uploads, deletions and submission |
| `audit_logs` | No change; new application actions `DOCUMENT_DOWNLOAD`, `DOCUMENT_REJECTED` (`action_type` has no check constraint) |

### Endpoints

| Endpoint | Change | Access |
|----------|--------|--------|
| `POST /api/v1/awards/{id}/documents` | Implemented (was planned); `int64` ids; 400, 409, 413, 503 (and 422 from 3.1.3) as problem details with a `code` | `award:update:own`, owner, draft |
| `GET /api/v1/awards/{id}/documents` | Implemented | Award read rule |
| `GET /api/v1/documents/{id}` | Implemented; `int64` id; `image/webp` added | Award read rule |
| `DELETE /api/v1/documents/{id}` | Implemented; `int64` id; 409 | `award:update:own`, owner, draft |
| `DELETE /api/v1/awards/{id}` | Also removes objects after commit | Unchanged |

### Services and libraries

- `software.amazon.awssdk:s3` (BOM-managed), path-style access; `ObjectStorage` (`@Component`): put, get as stream, delete, list older than; `StorageProperties` (`app.documents.*`, endpoint and keys from `STORAGE_S3_*`)
- `DocumentService`, `DocumentContent` (signature detection, file name rules), `DocumentAccess` (reuses `AwardOwnership`), `DocumentController`, `DocumentSweeper` (`@Scheduled`)
- `AwardService.delete`: publishes the keys to remove after commit (`@TransactionalEventListener(AFTER_COMMIT)`)
- 3.1.3: `ClamAvScanner` (INSTREAM client over a socket, no library), `ClamAvHealthIndicator`
- Tests: TestContainers `minio/minio` next to PostgreSQL and Redis; `clamav/clamav` in one IT

### Frontend

- `features/awards/award-documents/` (new): list, upload queue, preview dialog; used by `award-form` and `award-detail`
- `documents.service.ts`: upload with `reportProgress`, list, download as blob, delete
- `award-form`: save-before-upload (AC-2.2), submit confirmation text (AC-2.8)
- i18n keys `uk` and `en`
- `frontend/nginx.conf`: `client_max_body_size 11m`

### External systems

MinIO (already in both Compose files), ClamAV (new service in both Compose files, 3.1.3).

## 7. Technical decisions

| # | Decision | Reasoning | Source |
|---|----------|-----------|--------|
| D-1 | One private bucket, key `awards/<awardId>/<UUID>`; no file name or user data in the key | Keys show in MinIO logs and backups; the name is personal data and stays in the database where erasure can reach it | DATA_DICTIONARY §2.3, PRIVACY_BY_DESIGN |
| D-2 | Downloads stream through the API; no pre-signed URLs; `storage_url` stays null | Every read is checked against the current scope and audited; MinIO stays inside the Compose network (ADR-021) | ADR-021, AUTH §3.3 |
| D-3 | Type decided by the file's leading bytes (`%PDF-`, `FF D8 FF`, `89 50 4E 47 0D 0A 1A 0A`, `RIFF….WEBP`); no Tika | Four formats need four signatures; Tika adds ~70 MB of parsers to the attack surface for nothing | DATA_DICTIONARY appendix |
| D-4 | Write the object first, then the row; compensate on rollback; remove objects after commit on deletion; sweep orphans daily | Storage and database cannot share a transaction; a leftover object is harmless and swept, a row without its object is an error | — |
| D-5 | Upload and deletion only on the owner's draft; submitted documents are frozen | The reviewer judges what was submitted; changes after a return are Epic 4 resubmission | Award state machine, RBAC_matrix "Upload Scanned Document" |
| D-6 | Readers of documents = readers of the award (`AwardOwnership.isReadable`); everyone else 404 | One rule for the award and its evidence; 404 avoids confirming that an award exists | Feature 2.1, RBAC_matrix note 5 |
| D-7 | Limits: 10 MB per file (dictionary), 10 files per award, one copy of a file per award | An award has a certificate and a few supporting pages; the limits bound storage at about 100 MB per award | DATA_DICTIONARY §2.3 |
| D-8 | Encryption at rest by MinIO (SSE with a static KMS key from the environment); TLS terminates at the proxy, MinIO traffic stays on the Compose network | A per-file DEK hierarchy with monthly rotation needs a KMS the demo does not have | SECURITY_ARCHITECTURE key hierarchy, ADR-021 |
| D-9 | ClamAV `clamd` in Compose, scan before any other content check, fail closed | US-003 DoD asks for a security review of the upload; a scan that is skipped when the scanner is down is no control. Scanning first makes the EICAR file testable under any name | US-003, roadmap task "virus scanning" |
| D-10 | Upload starts from the form, which saves a draft first | The phone flow of US-003 is "photograph and fill in"; a separate "save first" step is the drop-off point | US-003 |
| D-11 | `document_type` is chosen by the user; `PENDING` is kept for every upload | The type helps the reviewer now; `PENDING` is the queue a later parser reads (tracker decision 2026-10-02) | EPIC-03 tracker |

**Proposed deviations from the docs** (applied in the PR of the story that touches them, after approval):

1. **`openapi.yml` ids**: document ids and `/documents/{id}` become `int64`, like awards in 2.1.0 and the GDPR export's `api_path` (tracker deviation 1). — 3.1.1
2. **`openapi.yml` `Document`**: `filename`/`originalFilename` become one `fileName` (the sanitised original), `awardId` added, `status` removed until OCR exists, `uploadedBy` is `UserRef`; `DocumentType` stays (tracker deviation 2). — 3.1.1
3. **Formats**: PDF, JPEG (`.jpg`, `.jpeg`), PNG and WEBP everywhere, as in V006 and the dictionary; `openapi.yml` and the state machine diagram list WEBP; the download lists `image/webp` (tracker deviation 3). — 3.1.1
4. **DATA_DICTIONARY §2.3**: new columns and unique index, `storage_url` "unused: downloads go through the API", key format, `processing_status` "stays `PENDING` until OCR". — 3.1.1
5. **THREAT_MODEL**: a new component analysis "Document upload" (STRIDE: spoofed type, oversized body, malicious content, path traversal in names, enumeration of ids, storage exposure) with the controls of this feature, replacing the roadmap's reference to a non-existent "T-12" (tracker deviation 4). — 3.1.1, scanner row in 3.1.3
6. **SECURITY_ARCHITECTURE key hierarchy**: the per-file DEK with monthly rotation is marked as the target design; the demo uses MinIO server-side encryption with one key (D-8). — 3.1.1
7. **State machine diagram**: a note that upload ends in `UPLOADED` = `processing_status PENDING`, the 80 % threshold of the diagram vs 0.7 of the dictionary is settled when OCR is planned (tracker deviation 5). — 3.1.1
8. **RBAC_matrix.md**: rows "Download award documents" (award readers) and "Delete own document (draft)". — 3.1.1

**Assumptions**

- A1. Nobody needs to attach a file to an award after submission until Epic 4's return flow; a forgotten certificate is attached after a return.
- A2. Phone cameras in the browser deliver JPEG (iOS converts HEIC on capture through a file input).
- A3. Scanned certificates are rarely over 10 MB; a larger PDF is re-exported by the user.
- A4. ClamAV needs about 1.2 GB of memory; the demo server (ADR-021) has room for it until Elasticsearch arrives in Epic 5, when the server size is reviewed.

## 8. Test plan

| AC | Unit | Slice | IT | FT | E2E |
|----|------|-------|----|----|-----|
| 1.1 | ✓ key format, checksum while streaming | ✓ controller 201, `Location` | ✓ row + object in MinIO (TestContainers) | ✓ owner uploads PDF, JPEG, PNG, WEBP | |
| 1.2 | ✓ size rules | ✓ 400 empty | | ✓ 10 MB + 1 byte → 413; 12 MB body → 413 problem | |
| 1.3, 1.6 | ✓ signatures, mismatch, appended extension, name sanitising (table-driven) | ✓ 400 codes | | ✓ PNG named `.pdf` → 400 | |
| 1.4, 1.7, 1.9 | ✓ access rule | ✓ 400 non-numeric | | ✓ owner, someone else 404, submitted 409, admin 403, dean of the faculty list 200, other faculty 404 | |
| 1.5 | ✓ limit and duplicate | | ✓ concurrent duplicate → one 409 (unique index) | ✓ 11th → 409; same file twice → 409 with id | |
| 1.8 | | ✓ headers | ✓ `DOCUMENT_DOWNLOAD` row | ✓ bytes equal, headers, dean downloads | |
| 1.10, 1.11 | ✓ compensation and sweep with a fake storage | | ✓ draft deletion removes objects; DB failure removes the object; sweep removes an old orphan, keeps a young one; MinIO stopped → 503 | | |
| 1.12 | | | ✓ bucket created, no policy | | |
| 1.13 | | | | ✓ export `api_path` downloads | |
| 1.14 | | | ✓ trigger rows with actor | | |
| 1.15 | | | | ✓ 10 MB upload < 5 s | |
| 1.16 | | | | | ✓ 9.5 MB upload through nginx |
| 2.1–2.6 | ✓ component: queue, client refusals, progress, error mapping, retry, delete dialog | | | | ✓ new award → add certificate (saves the draft) → list → preview → delete → upload again → submit |
| 2.7, 2.9 | ✓ read-only list, blob download, 404 reload | | | | ✓ dean opens the submitted award and downloads the certificate |
| 2.8 | ✓ confirmation text | | | | ✓ submit without documents shows the notice |
| 2.10, 2.11 | ✓ English keys | | | | ✓ English run; axe check; keyboard opens the picker |
| 3.1, 3.2 | ✓ INSTREAM protocol against a fake socket server (clean, found, error, timeout) | | ✓ real `clamav/clamav`: EICAR → found; clean PDF → OK; `DOCUMENT_REJECTED` row | ✓ scanner stubbed down → 503 | ✓ EICAR upload shows the malware message |
| 3.3, 3.4 | | | | | ✓ through `e2e.ps1` |

Coverage target 85 % lines per `mvn verify`; static analysis clean. Every story touches upload handling, so the security review agent runs on each diff.

## 9. Manual verification

Preconditions: `docker compose up -d postgres redis mailpit minio` (plus `clamav` after 3.1.3), backend `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` on `http://localhost:8080`, frontend `npm start` on `http://localhost:4200`. Seed accounts (password `Passw0rd-demo`): `employee.fmi@chnu.edu.ua` (faculty 9), `secretary.fmi@chnu.edu.ua`, `dean.fmi@chnu.edu.ua`, `admin@chnu.edu.ua`; `secretary.fpp` (faculty 10) as in Feature 2.1 §9. Swagger `http://localhost:8080/swagger-ui.html`; MinIO console `http://localhost:9001` (`minioadmin`/`minioadmin`); psql `docker compose exec postgres psql -U postgres award_monitoring`. Test files: a PDF scan of 1–3 MB, a phone photo (JPEG), a PNG, a 12 MB PDF, a PNG renamed to `fake.pdf`, a `.heic` or `.docx` file, the EICAR file (`X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*` saved as `eicar.pdf`).

1. As `employee.fmi` open `http://localhost:4200/awards/new`, type a title only, drop the PDF scan on «Документи» with type «Сертифікат». Expected: the address changes to the draft's edit route, a progress bar, then the file in the list with type, size and today's date. (AC-2.1, 2.2, 2.4, 1.1)
2. MinIO console → bucket `award-documents`. Expected: one object under `awards/<id>/` with a UUID name, no file name in the key; the bucket's access policy is private. psql `select file_name, file_type, mime_type, document_type, storage_key, checksum_sha256, processing_status, storage_url from documents;` Expected: values from the content, `PENDING`, `storage_url` null. (AC-1.1, 1.12)
3. Add the JPEG photo and the PNG in one drop. Expected: they upload one after another, each announced; type defaults to «Додатковий документ». (AC-2.4)
4. Drop the 12 MB PDF and the `.docx`. Expected: both refused in the browser with their reasons, no request in the network tab. (AC-2.3)
5. Swagger as `employee.fmi`: upload the 12 MB PDF to the draft → 413 `FILE_TOO_LARGE`; upload `fake.pdf` → 400 `CONTENT_MISMATCH`; upload the `.docx` → 400 `UNSUPPORTED_TYPE`; upload the PDF of step 1 again → 409 `DUPLICATE_DOCUMENT` with its id. (AC-1.2, 1.3, 1.5)
6. In the form, click the photo's name. Expected: preview dialog with the image. Click «Завантажити» on the PDF. Expected: saved under its original Ukrainian name, opens. (AC-2.6, 1.8)
7. Delete the PNG («Видалити документ «…»?» → confirm). Expected: gone from the list; MinIO object gone; psql `select action_type, user_id from audit_logs where entity_type = 'documents' order by created_at desc limit 4;` shows `INSERT` rows and a `DELETE` with `employee.fmi`'s id. (AC-1.9, 1.14)
8. Complete and submit the award. Expected: no «Ви не додали…» notice (it has documents). Create a second award without documents and submit it. Expected: the confirmation shows the notice and submission works. (AC-2.8)
9. Open the first award's page. Expected: «Документи» with two files, download and preview, no «Видалити». Swagger: `DELETE /api/v1/documents/<id>` → 409 `AWARD_NOT_EDITABLE`; `POST …/documents` → 409. (AC-2.7, 1.4, 1.9)
10. As `dean.fmi` open the first award. Expected: the documents listed; download works. psql: `select action_type, user_id, details from audit_logs where action_type = 'DOCUMENT_DOWNLOAD' order by created_at desc limit 2;` Expected: rows for the employee (step 6) and the dean. (AC-1.7, 1.8, 2.7)
11. Browser devtools on the download in step 10. Expected: response headers `Content-Disposition: attachment; filename*=UTF-8''…`, `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-store`, `Content-Security-Policy: sandbox`; the request URL carries no token. (AC-1.8, 2.9)
12. As `employee.fmi` request the data export (profile page, 1.3.3); in Swagger call `GET` on one document's `api_path`. Expected: the file. (AC-1.13)
13. Create a draft with two documents, then delete the draft. Expected: MinIO folder `awards/<id>/` empty. (AC-1.10)
14. After 3.1.3: upload `eicar.pdf` in the form. Expected: «Файл містить шкідливий код і не був завантажений», nothing in MinIO, psql shows `DOCUMENT_REJECTED` with `Eicar-Test-Signature`. (AC-3.1, 2.5)
15. Switch to English and repeat steps 1 and 4. Expected: every text in English. (AC-2.10)
16. With the keyboard only: Tab to the drop zone, Enter opens the picker; Tab to «Завантажити» and «Видалити». (AC-2.11)
17. On a phone (or Chrome device mode with a camera), «Сфотографувати» opens the camera; the photo uploads as JPEG. (AC-2.1)

### Detours

18. Swagger as `secretary.fpp`: `GET /api/v1/awards/<first award>/documents` → 404; `GET /api/v1/documents/<id>` → 404. As `employee.fmi` on someone else's draft → 404; as `admin` `POST` → 403; `GET /api/v1/documents/abc` → 400. (AC-1.4, 1.7)
19. Open `http://localhost:4200/awards/<first award>` as `dean.fmi`; in another browser as `admin` end the dean's role; click «Завантажити». Expected: «Документ більше не доступний» (or the login page when the role change signed the dean out), the list reloads empty or the page answers «Не знайдено». Re-assign the role. (§5, AC-2.9)
20. `docker compose stop minio`, upload a file. Expected: «Не вдалося завантажити файл» with «Спробувати ще раз»; Swagger 503 `STORAGE_UNAVAILABLE`; no new row. `docker compose start minio`, retry → uploads. (AC-1.11, 2.5)
21. After 3.1.3: `docker compose stop clamav`, upload a file. Expected: 503 `SCANNER_UNAVAILABLE`, «Не вдалося завантажити файл», nothing stored; `http://localhost:8080/actuator/health` shows `clamav` down. Start it again. (AC-3.2)
22. Delete an object by hand in the MinIO console, then download its document. Expected: 404 `DOCUMENT_CONTENT_MISSING` and an `ERROR` log line; the UI says «Документ більше не доступний». (§5)
23. Upload an object by hand to `awards/999/orphan` in the console; set `app.documents.sweep-cron` to run every minute and the age to `PT1M`, restart, wait 2 minutes. Expected: the orphan removed, the log reports 1 removed, documents with rows untouched. Restore the settings. (AC-1.11)
24. Start an upload of the 9.5 MB file and press reload in the middle. Expected: nothing stored or one complete document, never a row without its object; uploading the same file again is either accepted or answers «Цей файл уже додано». (§5)
25. Open the draft form in two tabs, upload the same file in both at once. Expected: one stored, the other «Цей файл уже додано». (§5)
26. Submit the draft in one tab while a file is uploading in the other. Expected: either the file is in the submitted award, or the upload answers «нагороду вже подано» (409) and nothing is left in MinIO. (§5)
27. Restart the backend with the award page open, then download. Expected: works after the restart; the token is refreshed if needed. (§5)
28. Let the access token expire on the form (15 minutes), then add a file. Expected: the upload succeeds after a silent refresh. (§5)
29. Back button after deleting a document. Expected: the list does not show the deleted document after the page reloads. (AC-1.9)

## 10. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| ClamAV memory (~1.2 GB) and slow signature load at start | Small server short of memory; first uploads after a restart answer 503 | Health check gates the backend; fail closed with a clear message; server size reviewed at Epic 5 (A4) |
| ClamAV image slows the IT run | Longer `verify` | One IT class uses the real scanner; everything else uses the fake socket server |
| Object storage and database disagree | Orphan objects or rows without content | D-4 ordering, compensation, daily sweep, `DOCUMENT_CONTENT_MISSING` logged as an error |
| Body limits differ between nginx, the proxy and Spring | 10 MB uploads fail in production only | AC-1.16 and the E2E upload of 9.5 MB through nginx; the production rehearsal of DEMO_DEPLOYMENT repeats it |
| Phone photos over 10 MB on new cameras | Upload refused | Clear message (AC-2.3); browser-side compression stays a later option |
| Personal data in scans (names, signatures) | GDPR exposure | Private bucket, encryption at rest, downloads audited, export links; erasure in Epic 6 |

## 11. Definition of Done

- `./mvnw verify` green (unit, slice, IT, FT), JaCoCo ≥ 85 % lines, Checkstyle/PMD/SpotBugs clean
- `npm run lint`, `npm run test:ci`, Playwright scenarios for AC-1.16, 2.1–2.11, 3.4
- Docs in the same PR as the story: `openapi.yml` (document paths and schemas), DATA_DICTIONARY §2.3, V025, THREAT_MODEL, SECURITY_ARCHITECTURE note, RBAC_matrix.md rows, state-machine note, DEMO_DEPLOYMENT (MinIO KMS key, ClamAV), `.env.prod.example`, `CHANGELOG.md`, EPIC-03 tracker, `BACKLOG.md`
- Security review of each story's diff (upload handling, US-003 DoD)
- §9 walked through by the author after the validation, including the detours
