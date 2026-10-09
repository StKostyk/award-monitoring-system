# Feature 4.3: Achievements — Colleague Visibility, Unit and Public Pages

> **Epic**: 4 — Approval Workflow Engine (SCRUM-47)
> **Sprint**: 5 (2026-10-12 → 2026-10-18)
> **Points**: 10 (two stories)
> **Status**: Approved 2026-10-09
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-04 tracker (decisions of 2026-10-05 on colleague visibility and organisational awards, risk 3), design review §I (colleague visibility options b + d), roadmap § Feature 2.3 ("know when my achievements will be publicly visible") and § Feature 5.1 ("shareable achievement summaries"), BRD §2 (public transparency), DATA_DICTIONARY §1.1, §1.3, §2.1, §2.2, §4.1, §4.2, PRIVACY_IMPACT (R001, visibility controls), DATA_GOVERNANCE §2 (classification), AUTHENTICATION_AUTHORIZATION (`/api/public/**`), RBAC_matrix.md, openapi.yml `/awards`, `/awards/{id}`, `/organizations`, `Award`, `AwardRecipient`, `UnitRef`, `RateLimitFilter`, `ProtectionProperties`, `AwardOwnership`, `PersonalDataAssembler`

## 1. Problem and personas

An approved award is seen by its owner and by the reviewers whose scope covers it, nobody else. Colleagues do not learn of each other's achievements, a faculty cannot show what it and its departments received, and nothing of the university's recognition record is visible outside the system. The BRD promises full public transparency, while the privacy assessment requires the person's control over what is published. The tracker decision of 2026-10-05 settles this: the owner opts in per approved award, a separate choice publishes it, and unit awards are visible once approved.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | Show an approved award to colleagues, publish it if she wishes, and take either back at any time |
| Colleague (any signed-in employee) | One «Досягнення» page with the awards colleagues shared and the awards of faculties and departments |
| Prof. Martynyuk, dean | A page of his faculty's achievements (unit awards and shared personal awards of its people) to point to |
| Visitor (applicant, partner, journalist) | A public page of the university's achievements without an account |
| Dmytro, GDPR officer | Every change of visibility recorded; nothing published beyond what the owner chose; e-mail and documents never shown |

## 2. Scope

**In (this feature)**

- Visibility of a personal approved award chosen by its owner: `PRIVATE` (default), `UNIVERSITY` («Показувати колегам»), `PUBLIC` («Показувати публічно», includes colleagues); `awards.visibility` (V035); an audit row per change; part of the GDPR export
- Unit awards: visible to colleagues and publicly once approved, by rule; no choice
- «Досягнення» page for signed-in users (`/achievements`) and its API (`GET /achievements`): filters by unit (with its departments), year, recognition level and recipient type
- Unit achievement pages (`/units/:id/achievements`) for faculties and departments
- Public pages without sign-in (`/public/achievements`, `/public/units/:id/achievements`) and their API (`GET /public/achievements`) with its own rate limit
- A reduced projection (`Achievement`) for every page: no e-mail, no person id, no documents, no request, reviewers, comments or impact score
- Privacy documents (PRIVACY_IMPACT, DATA_GOVERNANCE), data dictionary, RBAC note, user guide section

**Out (where it goes)**

- Text search over achievements — Epic 5 (search engine decision)
- Personal profile pages ("all shared awards of one person") and shareable personal summaries — Epic 5 (Feature 5.1)
- Counts and charts per unit — Epic 5 (analytics); unit awards counted separately there
- Visibility per department only (PRIVACY_IMPACT "departmental") — not offered (deviation 2)
- Choosing visibility before approval — the owner decides on the approved award (deviation 6)
- Search-engine indexing, Open Graph previews — need SSR, which is settled out
- A link to the public page from the server-rendered sign-in page — with the Sunday findings if wanted
- Visibility of the documents of an award — never published

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-56 (#150) | 4.3.1 Colleague visibility and the achievements page | 5 | yes | — |
| SCRUM-57 (#151) | 4.3.2 Unit achievement pages and public achievements | 5 | yes | 4.3.1 (projection, query, page component) |

Both split at the green backend gate; the UI lane may start from the stubs of §8.1.

## 4. Acceptance criteria

**Shared award**: an `APPROVED` award that is either a personal award with `visibility` `UNIVERSITY` or `PUBLIC` whose owner's account is not `DELETED`, or a unit award (`recipient_org_id` set). **Public award**: the same with `PUBLIC` in place of `UNIVERSITY` or `PUBLIC`; unit awards are public. **Unit filter**: a faculty includes its departments; a personal award belongs to the unit of its `organization_id` (the owner's department at submission).

### 4.3.1 Colleague visibility and the achievements page (SCRUM-56)

- **AC-1.1** Given my own approved personal award, when I open its page, then a «Видимість» section shows the current choice («Лише мені та рецензентам» by default), with «Показувати колегам» and «Показувати публічно». Given a draft, pending or rejected award, or a unit award, then the section is absent (a unit award shows «Нагорода підрозділу: видно всім після затвердження» instead).
- **AC-1.2** Given my own approved personal award, when I `PUT /awards/{id}/visibility` with `UNIVERSITY`, then 200 returns the `Award` with `visibility: UNIVERSITY`; one `AWARD_VISIBILITY_CHANGED` audit row holds the old and new value; the award `version` and its version history are unchanged. When I send the current value again, then 200 and no audit row.
- **AC-1.3** Given I choose «Показувати публічно», when I confirm, then a dialog lists what is published (name, department, title, description, category, awarding organisation, date, link, verification mark) and what is not (e-mail, documents, reviewers, comments) before the change is sent; cancelling sends nothing.
- **AC-1.4** Given the award is not mine (scoped readers included), then 404; given it is not `APPROVED`, then 409 `urn:awards:problem:visibility-fixed` with `awardStatus`; given a unit award, then 409 `visibility-fixed` without `awardStatus`; given a missing or unknown value, then 422 `validation-failed`; given no `award:update:own`, then 403 (audited). Nothing changes in each case.
- **AC-1.5** Given I am signed in, when I `GET /achievements`, then I get a page of shared awards as `Achievement` (award id, both titles and descriptions, category with level, awarding organisation, date, link, `verified`, recipient: type, person name for a personal award, unit), newest award date first, then highest id; never an e-mail, a person id, documents, the request, reviewers, comments or the impact score. `Cache-Control: no-store`.
- **AC-1.6** Given filters `unit`, `year`, `level`, `recipient`, then only matching shared awards are listed, the unit filter covering a faculty's departments; given a `unit` that is not an existing faculty or department, then 404; given a malformed parameter, then 400 `invalid-parameter`. Page and size follow `PageParam`/`SizeParam`.
- **AC-1.7** Given an award listed on `/achievements`, when its owner switches it back to «Лише мені та рецензентам», then the next request no longer lists it.
- **AC-1.8** Given I am signed in, when I choose «Досягнення» in the navigation, then `/achievements` shows cards with the title in the current language (falling back to the other), recipient (person or unit), unit, category, awarding organisation, date and the verification mark; filters live in the URL query and survive a reload; an empty result shows «Поки що немає досягнень за цими умовами»; uk and en; usable at 360 px; axe clean.
- **AC-1.9** Given my awards list `/awards`, then each approved personal award shared with colleagues or publicly carries a chip «Колегам» or «Публічно».
- **AC-1.10** Given I export my personal data, then each award in the export carries its `visibility`.

### 4.3.2 Unit achievement pages and public achievements (SCRUM-57)

- **AC-2.1** Given no sign-in, when I `GET /public/achievements`, then I get public awards only (never a `UNIVERSITY` one), with the projection, order, filters, errors and `Cache-Control` of AC-1.5 and AC-1.6.
- **AC-2.2** Given a request carrying an expired or malformed bearer token, when it calls `GET /public/achievements`, then 200 as for an anonymous request (the public chain ignores tokens).
- **AC-2.3** Given more than `app.auth.protection.public-requests-per-minute` (default 120) requests from one address within a minute, then 429 `too-many-requests` with `Retry-After`; the authentication endpoints keep their own budget.
- **AC-2.4** Given I am signed in, when I open `/units/:id/achievements` for a faculty or department, then the header shows its name in the current language, its faculty (for a department) as a link and, for a faculty, its departments as links; the list is `/achievements` filtered by that unit, with the other filters. Given an unknown id or another organisation type, then the not-found page.
- **AC-2.5** Given no sign-in, when I open `/public/achievements` or `/public/units/:id/achievements`, then the same pages show public awards only, with «Увійти» and the language switch and no signed-in navigation; unit names link to the public unit pages.
- **AC-2.6** Given I am signed in, when I open a public page, then it shows the public set with a link «Переглянути як співробітник» to the matching signed-in page; on `/achievements` unit names link to `/units/:id/achievements` and a link «Публічна сторінка» leads to the public page with the same filters.
- **AC-2.7** Given an award is `PUBLIC`, when its owner reduces it to `UNIVERSITY`, then it disappears from the public API at the next request and stays on `/achievements`.
- **AC-2.8** Public and unit pages: uk and en, usable at 360 px, axe clean, no console errors when anonymous (no token refresh attempt).

## 5. Edge cases

| Case | Expected |
|------|----------|
| Owner's account erased (`DELETED`) later | Personal awards drop from every page (query excludes `DELETED`); the future erasure job also resets `visibility` to `PRIVATE` (noted in DATA_DICTIONARY) |
| Owner `SUSPENDED`, `RETIRED`, `MEMORIAL` | Shared awards stay listed (records of achievements); a suspension is an account matter, not a publication one |
| Owner transferred to another department | Listed under the department at submission (`organization_id` is kept, DATA_DICTIONARY §2.1) |
| Award returned to a workflow state later (no such path today) | Query filters `status = 'APPROVED'`; the stored choice is kept for a later approval |
| Two tabs change the visibility at once | Last write wins; each effective change writes its audit row; no version check (not award content) |
| Visibility change while a reviewer reads the award | Unaffected: no version bump, no conflict with decisions or corrections (only pending awards are corrected) |
| A delegate or scoped reader calls `PUT …/visibility` | 404: only the owner chooses; delegations never carry it |
| Inactive faculty or department | Unit page still shows its awards (historical); `GET /organizations` lists only active units, so the header falls back to the name in the projection |
| Unit id of the university, a college or a speciality | 404 (units receive awards only as faculty or department, V027) |
| Title in one language only | Shown in either language with the fallback of `award-fields.ts` |
| `externalUrl` | Shown as a link with `rel="noopener noreferrer nofollow"`, scheme already restricted to `http`/`https` |
| Large page size, deep page | `SizeParam` clamps to 100; ordering is total (date, id), so pages do not repeat rows |
| Anonymous visitor's language | Transloco language switch works without sign-in; default `uk` |
| Signed-in user's expired token on a public page | The public service sends no token; no refresh, no redirect to sign-in |
| Rate limit behind the Compose proxy | Client address from `ClientRequest.from` (forwarded header already handled for the auth limit) |
| Description containing personal data of others | The owner's choice; the confirmation dialog shows the description is published |

## 6. Dependencies

### Tables

| Table | Change |
|-------|--------|
| `awards` | `visibility` (V035, 4.3.1); existing `idx_awards_approved` (award_date desc where approved) serves both queries |
| `organizations` | Read: `parent_org_id`, `org_type` for the unit filter |
| `users` | Read: names, `account_status` |
| `award_categories` | Read: level filter |
| `audit_logs` | New action `AWARD_VISIBILITY_CHANGED` |

### Endpoints

| Endpoint | Status | Story |
|----------|--------|-------|
| `PUT /awards/{id}/visibility` | New | 4.3.1 |
| `GET /achievements` | New | 4.3.1 |
| `GET /public/achievements` | New | 4.3.2 |
| `GET /awards/{id}`, `GET /awards` | `Award.visibility` added | 4.3.1 |
| `GET /users/me/export` | `visibility` per award | 4.3.1 |
| `GET /organizations` | Existing (anonymous), unit headers | 4.3.2 |

### Services

- `AwardVisibility` (`award/service`): owner check through `AwardOwnership`, status rule, audit row
- `Achievements` (`award/service`) with a specification or JDBC query shared by both endpoints (`shared` vs `public` predicate), unit subtree through `organizations.parent_org_id`
- `AchievementController` (signed-in) and `PublicAchievementController` (`/api/v1/public`)
- `SecurityConfig`: a `/api/v1/public/**` chain ordered before the API chain, GET only, no resource server, CORS as the API
- `RateLimitFilter` instance for `/api/v1/public/*` with its own key prefix and `ProtectionProperties.publicRequestsPerMinute`
- `PersonalDataAssembler`: `visibility` per award

### Frontend

- `features/achievements`: `achievements.routes.ts`, `achievement-list` (page, filters, cards), `unit-header`, `achievements.service.ts` (signed-in and public, the public one without the auth interceptor via an `HttpContext` flag), `visibility-section` and `publish-dialog` in `features/awards/award-detail`
- Routes `/achievements`, `/units/:id/achievements` (authGuard), `/public/achievements`, `/public/units/:id/achievements` (no guard); navigation item «Досягнення»
- Chips in `award-list`; i18n keys uk/en

### External systems

None.

## 7. Technical decisions

Inherited: ADR-022 (modular monolith: the `award` module owns achievements), ADR-015 (NgRx per feature; achievements are read-only lists, so a component store or plain signals suffice, as in `reviews`), ADR-023 (transition table untouched: visibility is not a workflow state), tracker decisions of 2026-10-05 (opt-in per award, separate public opt-in, unit awards public once approved).

**Proposed deviations and assumptions**

1. **Opt-in instead of "all awards public".** DATA_DICTIONARY §2.1 ("Awards are publicly visible"), V005 comments and BRD §2 ("100 % public award visibility") say every award is public. Per the tracker decision the data dictionary rule becomes "visible beyond owner and reviewers only by the owner's choice; unit awards once approved"; the BRD wording gets a note in the documentation review at the end of the epic (its success metric becomes "share of approved awards made public"). V005 comments stay (committed migration); V035 comments the column.
2. **Three levels per award, not four per person.** PRIVACY_IMPACT sketches a per-user setting with public / institutional / departmental / private. Here: per award, `PRIVATE` / `UNIVERSITY` / `PUBLIC`. Departmental is dropped: the department's reviewers already see its awards, and one colleague circle keeps the page simple. PRIVACY_IMPACT is updated with 4.3.2.
3. **Consent recorded as the per-award choice plus its audit row, not in `consent_records`.** The data dictionary lists `PUBLIC_VISIBILITY` as a required consent type. A single yes/no per person does not fit a per-award choice, and nothing writes `consent_records` yet. The audit row (who, when, old → new, address) is the record of consent; the type is marked "not used: see `awards.visibility`" in §4.2.
4. **No `version` bump, no award version for a visibility change.** It is not award content, and a bump would make open review or edit screens stale for nothing. Last write wins.
5. **Unit awards carry no stored choice.** `visibility` stays `PRIVATE` for them (`ck_awards_visibility_personal`), the rule lives in the query, and `Award.visibility` is `null` for a unit award. One source of truth; no write in the approval path.
6. **Choice on approved awards only.** The tracker says "per approved award"; choosing at submission would publish nothing until approval and adds a field to the form. The owner gets the approval e-mail and opens the award.
7. **No colleague award page.** Colleagues see the projection on the card; `GET /awards/{id}` keeps its rules (owner and scoped readers). Documents are never reachable through achievements.
8. **Public API path `/api/v1/public/**`** as AUTHENTICATION_AUTHORIZATION sketches (`/api/public/**`), in its own filter chain that ignores bearer tokens, with a per-address limit of 120 requests a minute (new property). Responses `Cache-Control: no-store`, so a withdrawn choice takes effect at once.
9. **`DELETED` owners hidden, other account states shown** (§5). Erasure itself is not built yet; its `fn_anonymize_user_data` gets the visibility reset when the erasure story comes (noted in the data dictionary, no change now).
10. **Roadmap.** The roadmap has no Feature 4.3 (added at the Epic 4 kickoff); the unit and public pages cover part of 5.1 "shareable achievement summaries". The roadmap gets a Feature 4.3 entry with 4.3.1.

## 8. Contract

### 8.1 OpenAPI stubs (written to `openapi.yml` in this PRD's PR, `x-status: planned`)

New operations and schemas are stubbed now. `Award.visibility` and the export field enter `openapi.yml` with 4.3.1, as the contract tests compare those schemas with the DTOs.

- `PUT /awards/{id}/visibility`, body `AwardVisibilityUpdate` → 200 `Award`; 401, 403, 404, 409 (`visibility-fixed`), 422. 4.3.1.
- `AwardVisibilityUpdate`: `visibility` (`AwardVisibility`, required).
- `AwardVisibility`: enum `PRIVATE`, `UNIVERSITY`, `PUBLIC`.
- `GET /achievements`, query `page`, `size`, `unit` (int64), `year` (integer 1950–2100), `level` (`RecognitionLevel`), `recipient` (`PERSON`|`UNIT`) → 200 `AchievementPage`; 400, 401, 404. 4.3.1.
- `GET /public/achievements`, same query, `security: []` → 200 `AchievementPage`; 400, 404, 429. 4.3.2.
- `Achievement`: `awardId` (int64), `title`, `titleUk`, `description`, `descriptionUk` (nullable strings), `category` (`AwardCategoryRef`), `awardingOrganization` (string), `awardDate` (date), `externalUrl` (uri, nullable), `verified` (boolean), `recipient` (`AchievementRecipient`).
- `AchievementRecipient`: `type` (`PERSON`|`UNIT`), `personName` (string, null for a unit), `unit` (`UnitRef`: the recipient unit, or the person's department at submission).
- `AchievementPage`: `Page` + `content` of `Achievement`.

### 8.2 Migration outlines

| File | Story | Content |
|------|-------|---------|
| `V035__award_visibility.sql` | 4.3.1 | `ALTER TABLE awards ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE'`; `ck_awards_visibility CHECK (visibility IN ('PRIVATE','UNIVERSITY','PUBLIC'))`; `ck_awards_visibility_personal CHECK (recipient_org_id IS NULL OR visibility = 'PRIVATE')`; `COMMENT ON COLUMN`; no new index (plan checked against `idx_awards_approved` with the seed data in the story) |

4.3.2 has no migration.

## 9. Test plan

| AC | Unit | Slice | IT | FT | E2E |
|----|------|-------|----|----|-----|
| 1.2, 1.4 | ✓ `AwardVisibility` rules (status, unit, same value) | ✓ 422, 409 mapping | ✓ audit row, version unchanged, `ck_awards_visibility_personal` | ✓ owner PUT; scoped reader 404; pending 409; 403 without permission | |
| 1.5–1.7 | ✓ predicate builder (shared vs public, unit subtree) | ✓ 400 on bad params | ✓ query: deleted owner hidden, department in faculty, order with equal dates | ✓ projection has no e-mail or ids; `no-store`; switch back removes | |
| 1.10 | ✓ assembler | | | ✓ export field | |
| 1.1, 1.3, 1.8, 1.9 | ✓ section, dialog, cards, filters, chips | | | | ✓ employee shares, colleague sees on `/achievements`; publish dialog; English; 360 px; axe |
| 2.1, 2.2 | | ✓ public chain with invalid token → 200 | | ✓ anonymous: public only, `UNIVERSITY` absent | |
| 2.3 | ✓ filter with its own prefix and limit | | ✓ Redis window | ✓ 429 with `Retry-After` (low limit profile) | |
| 2.4–2.8 | ✓ unit header, links, public service without token | | | | ✓ anonymous public page and unit page; signed-in links; English; 360 px; axe; no console errors |

Coverage target 85 % lines; static analysis clean. 4.3.1 (access rule, migration) and 4.3.2 (security config, anonymous endpoint, rate limit) go through the security review agent.

## 10. Manual verification

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`, backend `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` on `http://localhost:8080`, frontend `npm start` on `http://localhost:4200` (or the full Compose stack on `http://localhost`). Seed accounts (password `Passw0rd-demo`, `@chnu.edu.ua`): `employee.fmi`, `secretary.fmi`, `secretary2.fmi`, `dean.fmi`. Swagger `http://localhost:8080/swagger-ui.html`; psql `docker compose exec postgres psql -U postgres award_monitoring`. Faculty FMI is organisation 9, one of its departments 64. Preparation: as `employee.fmi` create and submit awards P1 and P2 (faculty-level category, P1 with a link), as `secretary.fmi` approve both; as `secretary.fmi` create and submit unit award U for department 64, as `dean.fmi` approve it. A second browser (or a private window) for the anonymous visitor.

1. As `employee.fmi` open P1. Expected: «Видимість: Лише мені та рецензентам» with the two other options. Open a draft of yours. Expected: no «Видимість». (AC-1.1)
2. On P1 choose «Показувати колегам». Expected: saved notice; psql `select action_type, old_values, new_values from audit_logs where action_type = 'AWARD_VISIBILITY_CHANGED' order by created_at desc limit 1;` shows `PRIVATE` → `UNIVERSITY`; `select version from awards where award_id = <P1>;` unchanged; «Історія змін» has no new entry. (AC-1.2)
3. On P2 choose «Показувати публічно». Expected: the dialog lists what is and is not published; «Скасувати» keeps «Лише мені…»; repeat and confirm. Expected: «Публічно». (AC-1.3)
4. Open `/awards`. Expected: chips «Колегам» on P1, «Публічно» on P2. (AC-1.9)
5. As `secretary2.fmi` choose «Досягнення». Expected: P1, P2 and U as cards, newest date first, with the owner's name, department, category, date; P1's link opens in a new tab; U shows the department as recipient. (AC-1.5, 1.8)
6. Filter by FMI faculty, then the year of P1, then «Підрозділи». Expected: the URL query changes; reload keeps the filters; «Підрозділи» leaves only U; a year with nothing shows the empty message. (AC-1.6, 1.8)
7. Swagger `GET /api/v1/achievements` as `secretary2.fmi`. Expected: no `email`, no person id, no `impactScore`, no `request`; header `Cache-Control: no-store`. `?unit=1` (the university) → 404; `?year=abc` → 400. (AC-1.5, 1.6)
8. In the anonymous browser open `http://localhost:4200/public/achievements`. Expected: P2 and U, not P1; «Увійти» and the language switch; no signed-in navigation; console without errors. (AC-2.1, 2.5, 2.8)
9. Click the department of U. Expected: `/public/units/64/achievements` with the department name and its faculty as a link; only U and, if P2 belongs to 64, P2. Click the faculty. Expected: `/public/units/9/achievements` with the department links. (AC-2.4, 2.5)
10. As `secretary2.fmi` on `/achievements` click U's department. Expected: `/units/64/achievements`, P1 included if it belongs to 64. «Публічна сторінка». Expected: the public page with the same filter; it offers «Переглянути як співробітник». (AC-2.4, 2.6)
11. As `employee.fmi` set P2 to «Показувати колегам». Reload the anonymous page. Expected: P2 gone; still on `/achievements`. Set P1 to «Лише мені та рецензентам»; reload `/achievements` as `secretary2.fmi`. Expected: P1 gone. (AC-1.7, 2.7)
12. As `employee.fmi` `GET /api/v1/users/me/export`. Expected: each award has `visibility`. (AC-1.10)
13. Switch to English on `/achievements`, a unit page and the public page; then 360 px. Expected: English texts, titles fall back to Ukrainian where English is empty, usable layout, filters reachable by keyboard. (AC-1.8, 2.8)

### Detours

14. Swagger `PUT /api/v1/awards/<P1>/visibility` as `secretary.fmi` (scoped reader) → 404; on a pending award of `employee.fmi` as its owner → 409 `visibility-fixed` with `awardStatus`; on U as `secretary.fmi` (its owner) → 409 `visibility-fixed`; with `{"visibility":"FRIENDS"}` → 422. Nothing changes in psql. (AC-1.4)
15. Send the same value twice. Expected: 200 both times, one audit row. (AC-1.2)
16. Open P1 in two tabs as `employee.fmi`; choose «Колегам» in one and «Публічно» in the other. Expected: both succeed; reload shows «Публічно»; two audit rows. (§5)
17. Anonymous: open `http://localhost:4200/units/64/achievements` (signed-in route) directly. Expected: sign-in; `http://localhost:4200/public/units/1/achievements` → the not-found page. (AC-2.4)
18. Anonymous: `curl -H "Authorization: Bearer x" http://localhost:8080/api/v1/public/achievements`. Expected: 200. (AC-2.2)
19. Anonymous: send 125 requests in a loop (`for i in $(seq 125); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/public/achievements; done | sort | uniq -c`). Expected: 120 × 200, then 429 with `Retry-After`; sign-in still works from the same address. (AC-2.3)
20. Signed in as `secretary2.fmi`, in DevTools run `sessionStorage.setItem('access_token', 'x'); sessionStorage.setItem('refresh_token', 'x')`, then open `/public/achievements`. Expected: the page loads, no redirect to sign-in; the `public/achievements` and `organizations` requests carry no `Authorization` header (the app's own start-up check of the stored session may try one refresh, which fails quietly). Open `/achievements`. Expected: sign-in. (§5, AC-2.8)
21. Stop the backend, reload `/public/achievements`. Expected: an error message with «Спробувати знову»; start the backend, retry works. (§5)
22. psql `update users set account_status = 'DELETED' where email_address = 'employee.fmi@chnu.edu.ua';` (restore afterwards with `'ACTIVE'`). Expected: P1 and P2 gone from both pages; U (owned by `secretary.fmi`) unaffected. (§5)
23. As `employee.fmi` set P2 public, then press Back and reload P2's page. Expected: «Публічно» shown; no duplicate audit row. (AC-1.2)

## 11. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Personal data published beyond the owner's choice | GDPR breach, trust | Projection type with no e-mail or ids; FT asserts the JSON field set; `UNIVERSITY` never in the public query (FT); security review of both stories |
| A withdrawn choice still visible | Owner's objection ignored | `no-store`, no cache layer; FT for the switch back |
| Anonymous endpoint scraped or flooded | Load, enumeration of names | Per-address limit; page size cap; only opted-in data |
| Public chain order in `SecurityConfig` | Public path falls into the API chain (401) or the API chain loses protection | Chain matcher limited to `GET /api/v1/public/**`; slice test both ways |
| Unit subtree query slow on many awards | Page latency | Two-level hierarchy (faculty → department); `idx_awards_approved`; plan checked in the story |
| Doc contradictions (BRD, PRIVACY_IMPACT, consent type) left unresolved | Thesis inconsistency | Deviations 1–3 applied in the stories' PRs; BRD note in the end-of-epic documentation review |

## 12. Definition of Done

- `.\tools\gate.ps1` green (unit, slice, IT, FT, JaCoCo ≥ 85 % lines, Checkstyle/PMD/SpotBugs clean, frontend lint, tests, prod build)
- Playwright scenarios for AC-1.3, 1.8, 2.5, 2.6, 2.8
- Docs in the same PR as the story: `openapi.yml` (stubs → implemented, `Award.visibility`, export), DATA_DICTIONARY §2.1 (column, rule), §4.1 (`AWARD_VISIBILITY_CHANGED`), §4.2 (`PUBLIC_VISIBILITY` note), V035, RBAC_matrix.md (achievements: any signed-in user; public: anonymous), AUTHENTICATION_AUTHORIZATION (public chain, limit), PRIVACY_IMPACT and DATA_GOVERNANCE (deviations 2, 3), roadmap Feature 4.3, user guide section «Досягнення та видимість», `CHANGELOG.md`, EPIC-04 tracker, `BACKLOG.md`
- Security review of the 4.3.1 and 4.3.2 diffs
- Audit row for every effective visibility change
- §10 manual verification run in the browser after the validation, including the detours
