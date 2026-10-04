# Epic 3: Document Processing & Parsing — Status

> **Started**: 2026-10-02
> **Done**: —
> **Author**: Stefan Kostyk
> **Jira epic**: SCRUM-34
> **Roadmap**: [DEVELOPMENT_ROADMAP.md § Epic 3](../../DEVELOPMENT_ROADMAP.md#epic-3-document-processing--parsing)

## Progress

| Feature | Status | Started | Done |
|---------|--------|---------|------|
| 3.0 Production configuration (deployment preparation) | Done (3.0.1) | 2026-10-02 | 2026-10-02 |
| 3.1 Document Upload & Storage | In progress ([feature-3.1](../features/epic-03/feature-3.1-document-upload-storage.md)) | 2026-10-02 | |
| 3.2 OCR & Intelligent Parsing | Deferred (see decisions) | | |
| 3.3 Confidence Scoring & Manual Review | Deferred (see decisions) | | |

## Current focus

Epic kickoff 2026-10-02. Story 3.0.1 (production configuration, ADR-021) is done. Feature 3.1 PRD approved 2026-10-02 (16 points, stories 3.1.1–3.1.3); 3.1.1 (storage backend and document API) is done; 3.1.2 (upload in the form and the award page) is in review; 3.1.3 follows.

## Scope

Epic 3 in this delivery is upload only: an award owner attaches the scanned certificate and supporting files to an award, readers of the award download them, the files live in S3-compatible object storage (MinIO in every environment, ADR-021) and only metadata is kept in `documents`. OCR, field extraction and confidence scoring (Features 3.2 and 3.3, US-006 and US-007, 29 points) are deferred until Epics 4, 7, 6, 5 and 8 are delivered; uploaded documents keep `processing_status = 'PENDING'` so a later parser can pick them up without a migration of existing rows.

Out of scope here: attaching documents to a returned request during resubmission (Epic 4, `documents.request_id`), the `verification_badge` set by a reviewer (Epic 4), erasure of documents of submitted awards (Epic 6, with 2.4.2), search over document contents (Epic 5).

## Stories

`parallel` marks stories whose UI can be built in a separate lane from the OpenAPI contract while the backend is in progress.

| # | Story | Feature | Pts | Jira | GitHub | Parallel | Status |
|---|-------|---------|-----|------|--------|----------|--------|
| 1 | 3.0.1 Production configuration and local production run | 3.0 | 3 | SCRUM-33 | #109 | no | Done |
| 2 | 3.1.1 Document storage and upload API | 3.1 | 8 | SCRUM-35 | #111 | no | Done |
| 3 | 3.1.2 Certificate upload in the award form and award page | 3.1 | 5 | SCRUM-36 | #112 | no | In review |
| 4 | 3.1.3 Malware scanning of uploads | 3.1 | 3 | SCRUM-37 | #113 | no | To do |

Total: 19 points, sprints 3–4. A fixes story follows the Feature 3.1 validation if it finds anything.

## Decisions

| Date | Decision | Rationale | Reference |
|------|----------|-----------|-----------|
| 2026-10-02 | Epic 3 delivers Feature 3.1 only; Features 3.2 and 3.3 are deferred until after Epic 8 and re-planned then (engine choice: a self-hosted OCR such as Tesseract or a cloud service) | Agreed delivery order "Epic 3 upload only, OCR later"; a paid OCR service conflicts with the no-spend constraint until the server is rented | Delivery plan, ADR-021 |
| 2026-10-02 | Story 3.0.1 (production configuration) belongs to this epic as Feature 3.0 | Deployment preparation was started after Epic 2; it has no feature of its own | ADR-021 |
| 2026-10-02 | Feature 3.1: private bucket keyed `awards/<awardId>/<UUID>`, downloads only through the API (audited, award read rule), type from file content, upload and deletion on the owner's draft only, 10 MB per file and 10 files per award, MinIO server-side encryption, ClamAV scanning that fails closed, the form saves a draft before the first upload | Evidence follows the award's access rule; no personal data in object keys; US-003 phone flow and its DoD security review | Feature 3.1 PRD D-1–D-11 |
| 2026-10-02 | `V006` `documents` is the base; new columns (document type, description) land in a new migration | Versioned migrations are immutable once merged | MIGRATION_STRATEGY |
| 2026-10-02 | MinIO image `cgr.dev/chainguard/minio:latest` (runs as root) in Compose and the tests | MinIO publishes no images on Docker Hub or quay.io any more; Chainguard builds the same server from source, with the same environment variables (proposed in the 3.1.1 PR) | ADR-021 revision |
| 2026-10-02 | Encryption at rest: the application sets SSE-S3 as the bucket default and requests it on every upload; MinIO holds the static key `MINIO_KMS_SECRET_KEY` | Does not depend on MinIO's auto-encryption setting; an upload fails instead of being stored unencrypted when the key is missing | PRD D-8, AC-1.12 |
| 2026-10-02 | The backend uses its own MinIO account (`minio-init`: bucket created with SSE-S3, policy on its objects only); the root account is never given to the backend | Security review of 3.1.1: a compromised backend could otherwise open the bucket or turn off encryption | DEMO_DEPLOYMENT |
| 2026-10-02 | Problem types are slugs as in Epic 2 (`file-too-large`, `unsupported-type`, `content-mismatch`, `empty-file`, `document-limit`, `duplicate-document`, `storage-unavailable`, `document-not-found`, `document-content-missing`, `missing-parameter`); the PRD's upper-case codes name the same types | One convention for `urn:awards:problem:*` | 3.1.1 |
| 2026-10-02 | The award form had no submit confirmation; AC-2.8 adds one that opens only for an award without documents | A dialog on every submission would add a click to the common case with a certificate | 3.1.2 |
| 2026-10-02 | The frontend CSP allows `blob:` images (`img-src 'self' data: blob:`) | The image preview shows the downloaded file from an object URL; scripts stay `'self'` only | 3.1.2 |
| 2026-10-02 | `tools/e2e.ps1` runs the frontend nginx configuration in a throwaway container (port 4280) in front of the local backend | AC-1.16: the 9.5 MB upload and the 413 answer are tested through nginx, not only through the dev server proxy | 3.1.2 |

## Documentation deviations to resolve

Each item is settled in the Feature 3.1 PRD (§7, deviations 1–8) and applied in the PR of the story that touches it.

1. `openapi.yml` document ids are UUIDs; `documents.document_id` is `BIGSERIAL` and the GDPR export already links `/api/v1/documents/{id}` with a number. Align the API to `int64`, as was done for awards in 2.1.0. Applied in 3.1.1.
2. `openapi.yml` `Document` has `type` (`CERTIFICATE`, `DIPLOMA`, `SUPPORTING_DOCUMENT`, `PHOTO`), `description` and `status` (`PENDING`, `VERIFIED`, `REJECTED`); `documents` has no type or description column and its `processing_status` has six other values. Applied in 3.1.1.
3. Allowed formats: the dictionary and `ck_documents_file_type` allow PDF, JPG, JPEG, PNG, WEBP; `openapi.yml` and the document state machine name PDF, JPG, PNG; the download response lists no `image/webp`. Applied in 3.1.1.
4. The roadmap cites `THREAT_MODEL.md T-12` for virus scanning; the threat model has no T-numbered threats and no upload threat. A file-upload scenario (STRIDE: tampering, DoS, malicious content) is added to the threat model with 3.1.1. Applied in 3.1.1.
5. The document state machine uses `UPLOADING`, `UPLOADED`, `PROCESSED` and an 80 % threshold; the dictionary uses `processing_status` values and 0.7. Upload uses the dictionary values; the diagram is redrawn when OCR is planned. Applied in 3.1.1.
6. `documents.storage_url` holds a pre-signed URL; downloads go through the API so every read is authorised and audited, and the column stays empty. Applied in 3.1.1.
7. The roadmap cites ADR-020 for the storage backend; ADR-021 supersedes it for the defense (MinIO in Compose). Applied in 3.1.1.

## Technical notes

- No S3 client is on the backend classpath and `application.yaml` has no storage properties; the environment variables `STORAGE_S3_ENDPOINT`, `STORAGE_S3_ACCESS_KEY`, `STORAGE_S3_SECRET_KEY` are already passed by both Compose files.
- Request body limits: Spring multipart defaults to 1 MB and the frontend nginx has no `client_max_body_size` (default 1 MB); both must allow 10 MB plus the multipart overhead, and the production reverse proxy as well.
- `fk_documents_awards` cascades on delete, so deleting a draft removes the rows but not the objects; object removal follows the row deletion after commit, with a sweep for leftovers.
- `trg_documents_audit` (V013) already writes `audit_logs` rows for every insert, update and delete of `documents`; downloads are reads and need an application audit entry.
- Integration tests run MinIO through TestContainers next to PostgreSQL and Redis.
- The GDPR export (1.3.3) lists document metadata with `api_path`; the download endpoint makes that link work.

## Risks

1. Malware scanning with ClamAV adds a container of about 1 GB of memory; on a small demo server this competes with Elasticsearch (Epic 5). The PRD weighs ClamAV against content-type sniffing alone.
2. Without OCR, the US-003 target "under 5 minutes on a phone" still depends on manual entry; the camera capture of 3.1.2 shortens it only by the attachment step.
3. Thesis claims on AI parsing (BRD success metric ≥ 90 % confidence on ≥ 70 % of documents) stay unproven until Features 3.2 and 3.3 are delivered; the thesis text must describe them as planned.

## Quick links

- [User stories US-006, US-007](../requirements/USER_STORIES.md#us-006-intelligent-document-parsing)
- [Data dictionary § documents](../database/DATA_DICTIONARY.md#23-entity-documents)
- [Document state machine](../diagrams/uml/state-machine-document.puml)
- [Award submission data flow](../diagrams/data-flow/dfd-level2-award-submission.puml)
- [OpenAPI](../api/openapi.yml)
- [Security architecture](../security/SECURITY_ARCHITECTURE.md) · [Threat model](../security/THREAT_MODEL.md)
- [ADR-021 Deployment target](../architecture/adr/ADR-021-Deployment-Target.md)
