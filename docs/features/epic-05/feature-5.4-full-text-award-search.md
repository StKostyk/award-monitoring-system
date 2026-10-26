# Feature 5.4: Full-Text Award Search

> **Epic**: 5 — Analytics & Reporting Dashboard (SCRUM-97)
> **Sprint**: 7 (2026-10-26 → 2026-11-01)
> **Points**: 5 (one story)
> **Status**: Approved 2026-10-25
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-05 tracker (decision of 2026-10-19 on search, deviation 1, risk 1), ADR-007 and its addenda of 2026-10 and 2026-10-05, ADR-004, ADR-022, BRD §5.1 (search under 1 s) and §5.2, DATA_DICTIONARY §2.1 (`awards`, indexes, visibility rules), PRIVACY_BY_DESIGN §4, `openapi.yml` (`/awards`, `/achievements`, `/public/achievements`), `AwardSpecifications`, `Achievements`, `DuplicateFinder`, `UserSpecifications` (`q`), `user-list.component.ts` (debounced search field), `achievement-list.component.ts` (filters in the address bar)

## 1. Problem and personas

Award lists can be filtered by status, category, date, unit, year, level and recipient, but not by what an award is called. Someone who remembers "the Shevchenko prize" or "Horizon grant" pages through the list; a colleague who wants to see what a named person has shared has no way to find it. ADR-007 left the search engine open until Epic 5, and the 2026-10-05 addendum chose to evaluate PostgreSQL full-text search first: one university holds tens of thousands of awards, the data stays in one store with the same access rules, backups and erasure.

| Persona | Need in this feature |
|---------|----------------------|
| Employee (lecturer, researcher) | Finds an own award, draft or approved, by a word of its title, awarding organisation or description, in Ukrainian or English |
| Colleague, dean, faculty secretary | Find shared achievements by title, awarding body or the name of the person who shared them, within the unit, year and level filters |
| Visitor (public page) | Finds public achievements by the same words, without signing in |
| GDPR officer | No new copy of personal data: the search columns are derived from the award row and disappear with it |

## 2. Scope

**In (MVP)**

- A `q` parameter on `GET /awards` (own awards), `GET /achievements` and `GET /public/achievements`, combined with the existing filters
- Full-text search in PostgreSQL over the English and Ukrainian titles, the awarding organisation and both descriptions, with prefix matching and a light query-side stem for Ukrainian inflection, a trigram fallback for typos and, on the achievements lists, the name of the person who shared the award
- Relevance order while searching
- A search field on "My awards" and on the achievements pages (signed-in, public, unit pages)
- Migration V047 (generated search columns and their GIN indexes) and an ADR-007 addendum recording PostgreSQL as the search engine

**Out (deferred)**

- The reviewer queue (`/reviews`): filtered by level, unit and state already; a search there is a later fix story if reviewers ask for it
- Document contents (OCR is deferred in Epic 3; BRD §4 "Unified search across award and institutional documents")
- Search in the admin user list beyond its existing `q`, faceted counts, highlighted matches, suggestions while typing
- Elasticsearch (ADR-007 addendum: only if PostgreSQL falls short) and cross-university search (ADR-022)

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-107 (#294) | 5.4.1 Full-text award search on PostgreSQL | 5 | no | — |

One story with a backend part (V047, query, three endpoints) and a UI part (two lists); the UI part starts once the backend gate is green.

## 4. Acceptance criteria

Common definitions:

- **Search text**: `q` with surrounding spaces removed. Under 2 characters it is ignored (the list behaves as without `q`); over 200 characters the request is refused.
- **Words**: the runs of letters and digits of the search text after lower-casing and removing the apostrophes `'`, `’`, `ʼ` and `` ` ``; any other character separates words. The first 10 words are used.
- **Stem**: a word of 5 or more characters loses up to two final characters out of `а е є и і ї о у ю я й ь`, keeping at least 4 (`премія` → `прем`, `шевченка` → `шевченк`, `award` → `award`).
- **Searched texts**: `title` and `title_uk` (weight A), `awarding_organization` (B), `description` and `description_uk` (C), apostrophes removed as above.
- **Match**: (a) every word's stem is the prefix of a word of the searched texts; or (c), on the achievements lists only, the search text is a case-insensitive fragment of the first and last name of a personal award's owner; only when neither (a) nor (c) finds an award of the list, (b) the trigram word similarity of the search text with the titles and awarding organisation is at least 0.5.
- **Relevance order**: matches by (a) or (c) by full-text rank (weights A > B > C) descending; matches by (b) by similarity descending; ties in the list's own order (newest first for own awards; award date, then award id for achievements).

### 5.4.1 Full-text award search on PostgreSQL (SCRUM-107)

- **AC-1.1** (Own awards) Given an account with awards in several statuses, when it calls `GET /awards?q=<text>`, then the page holds only its own awards that match, in every status including `DRAFT`, in relevance order, and `status`, `category`, `dateFrom` and `dateTo` narrow the result further; without `q` (or with a search text under 2 characters) the list and its order are unchanged.
- **AC-1.2** (Achievements keep their rule) Given approved awards with every visibility, awards of a `DELETED` account and unit awards, when `GET /achievements?q=<text>` and `GET /public/achievements?q=<text>` are called, then each returns only matching awards the same call without `q` would return (shared or public personal awards of accounts that are not `DELETED`, approved unit awards; public only on the public list), combined with `unit`, `year`, `level`, `recipient` and `person`, in relevance order; a private award, a draft or another person's pending award is never returned whatever the text.
- **AC-1.3** (Ukrainian and English) Given approved shared awards titled «Премія імені Тараса Шевченка», "Best Paper Award", with the awarding organisation "Horizon Europe" and a description containing «п’ять років», when searched with `премія`, `премії`, `Шевченко`, `award`, `best pap`, `horizon` and `пʼять`, then each search finds the award with that text: inflected and prefix forms match through the stem, both title languages, the awarding organisation and the descriptions are searched, and the three apostrophe forms are equal.
- **AC-1.4** (Typos) Given the awards of AC-1.3, when searched with `Horizn`, `Horrizon` or `Шевченкo` (a Latin `o`), then the award is found by trigram similarity (0.70 to 0.78 on the fixture), because no award matches the words; a text whose words match an award (`horizon`) lists only the full-text matches.
- **AC-1.5** (Relevance and paging) Given one award with the search word in its title and another with it only in its description, when searched, then the title match comes first; `totalElements` counts all matches, pages do not repeat or skip an award, and `size` stays capped at 100.
- **AC-1.6** (Names on achievements) Given shared awards of «Оксана Викладач», when `GET /achievements?q=виклад` is called, then her shared awards are listed (and only those the list rule admits); `GET /awards?q=виклад` by another account finds none of them; a unit award is never matched by a name.
- **AC-1.7** (Input handling) Given any of the three lists, when `q` has more than 200 characters, then 400 `urn:awards:problem:invalid-parameter` naming `q`; when `q` contains tsquery or SQL syntax (`&`, `|`, `!`, `:*`, `(`, `)`, `'`, `\`, `%`, `_`, `--`), then it is treated as separators and the answer is 200; when it has 2 or more characters but no letter or digit, Then the page is empty.
- **AC-1.8** (Speed) Given 100 000 approved awards in the database of an integration test, when a two-word search runs on `/achievements` and on `/awards`, then each answers in under 1 s (BRD §5.1), and the plan of the search uses the GIN indexes of V047.
- **AC-1.9** (Search columns) Given V047 applied, when an award is created, edited, submitted, approved, made private or erased, then its search columns follow in the same transaction (generated columns: a changed title is found by the next search, an erased text is no longer found); the application never writes them; the unused V005 indexes `ftidx_awards_title` and `ftidx_awards_title_uk` are dropped.
- **AC-1.10** (My awards page) Given the "My awards" page, when the user types into the search field «Search my awards», then after a pause of 300 ms the list shows the matches combined with the status, category and date filters, back on the first page; a text under 2 characters lists as without a search; the clear button empties the field and the list returns; no match shows «No awards match your search.».
- **AC-1.11** (Achievements pages) Given `/achievements`, `/public/achievements` or a unit page `/units/:id/achievements`, when the user types into the search field «Search achievements», then after 300 ms the list shows the matches, `q` appears in the address bar with the other filters (reload, back and a copied link keep it), paging returns to the first page, and no match shows the existing «No achievements match these filters yet.».
- **AC-1.12** (Accessibility and layout) Given both pages at 360 and 1280 px, uk and en, light and dark, when they are checked, then the search field is labelled, its clear button has an accessible name, there is no page-level horizontal scroll, no serious axe finding, and both languages show translated texts.

## 5. Edge cases

| Case | Expected | AC |
|------|----------|----|
| `q` of spaces only, or one character | Ignored, list as without `q` | 1.1, 1.10 |
| `q` of 201 characters | 400 `invalid-parameter` (`q`); the UI field caps input at 200 | 1.7 |
| `q` = `'; drop table awards; --` or `a & b | !c` | 200, words `drop`, `table`, `awards`… / `a`, `b`, `c`; no tsquery syntax error (the tsquery is built from words, never from raw text) | 1.7 |
| `q` = `??` | Empty page | 1.7 |
| 11 words | The first 10 used | Definitions |
| Draft saved a second ago with a new title | Found by the new title (generated column, same transaction) | 1.9 |
| Award made private while a colleague's result page is open | The next search or page no longer lists it; the detail link answers 404 (existing rule) | 1.2 |
| Owner suspended or erased | Personal awards leave the achievements results with the next request (existing account rule); erased texts are no longer found | 1.2, 1.9 |
| Search by a name that also occurs in a title | Both kinds of match listed, title matches ranked by full-text rank | 1.6 |
| Public list hammered with searches | The existing public rate limit (120 per minute and address) applies; 429 with `Retry-After` | existing |
| Mixed Latin and Cyrillic letters in one word (`Шевченкo` with a Latin `o`) | No full-text match; found by the trigram fallback when the rest of the word is long enough (0.78 here), not guaranteed for short words | 1.4 |
| `q` together with `person` on the public list | Both apply (the person's public awards that match) | 1.2 |

## 6. Dependencies

### Tables

| Table | Change | Story |
|-------|--------|-------|
| `awards` | V047: generated `search_vector` (`tsvector`) and `search_text` (`text`), GIN indexes on both, two V005 indexes dropped | 5.4.1 |
| `users` | None; the name match reads `first_name || ' ' || last_name` through the existing `trgm_users_name` (V012) | 5.4.1 |

### Endpoints

| Endpoint | Change |
|----------|--------|
| `GET /awards` | New `q` (`SearchQueryParam`), new 400 for `q` |
| `GET /achievements`, `GET /public/achievements` | New `q`; existing 400 now also names `q` |

### Services

- `AwardSpecifications`: a search predicate and the relevance order; `Achievements.shared/published` and the own-award list pass `q`
- A `SearchTerms` value (words, stems, tsquery text; built by a `*Helper` or a `@Component` parser) shared by the three lists; the tsquery is assembled from validated words only
- `DuplicateFinder` and `trgm_awards_title` stay unchanged

### Frontend

- `features/awards/award-list`: search field in the filter row, `q` in the store filters, `awards.service` passes it
- `features/achievements/achievement-list`: search field, `q` in `readQuery`/`writeQuery`, `achievements.service` passes it
- i18n keys `awards.filters.search`, `awards.noMatch`, `achievements.filters.search` and a clear-button label (an existing one if there is one), uk and en

### External systems

None. PostgreSQL 17 with `pg_trgm` (installed by V012); no Ukrainian dictionary in PostgreSQL 17 (checked: 29 Snowball configurations, none Ukrainian).

## 7. Technical decisions

Inherited:

- PostgreSQL full-text search with GIN plus `pg_trgm`; Elasticsearch stays out (ADR-007 addendum 2026-10-05, tracker decision 2026-10-19)
- One store, one tenant (ADR-004, ADR-022); access rules of the lists unchanged (DATA_DICTIONARY §2.1)
- Store-held filters on "My awards", address-bar filters on the achievements pages (ADR-015, existing components)

Settled here:

1. **`simple` configuration for both languages.** PostgreSQL 17 has no Ukrainian stemmer, and a Russian one would stem Ukrainian wrongly. All searched texts use `simple`; inflection is covered by the query-side stem plus prefix matching (`прем:*`), English plurals by the prefix. English stemming is given up for one rule in both languages.
2. **Generated stored columns** (`GENERATED ALWAYS AS … STORED`) instead of a trigger or expression indexes: always in step with the row in the same transaction, no application code writes them, erasure and retention remove them with the text, and the queries name a column instead of repeating a long expression. Hibernate does not map them for writing.
3. **The tsquery is built from words**, each `<stem>:*`, joined with `&`, passed as a bound parameter to `to_tsquery('simple', ?)`; raw user text never reaches tsquery syntax.
4. **Trigram fallback on the short texts only** (`search_text`: titles and awarding organisation, lower-cased), `word_similarity >= 0.5` through `%>` with the threshold set for the transaction (`set_config(…, true)`); descriptions are left out of the fallback to keep it from matching noise. It runs only when the full-text and name match find nothing: OR'd with the full-text match it rechecked every title holding a common word (10 000 candidates, 1.7 s on 100 000 awards), against 16 to 200 ms for the full-text match (measured 2026-10-26).
5. **Name match on achievements only**, whole search text as a fragment of `first_name || ' ' || last_name` (case-insensitive, `ILIKE` on the V012 trigram expression), the rule of the admin user list. The owner's name is already shown on these cards.
6. **Relevance order** as defined in §4; `ts_rank_cd` with the default weights; without `q` the existing order is untouched.
7. `q` limits: 2 to 200 characters, 10 words; the backend owns the rule, the UI mirrors the 2-character minimum and the 200-character cap.
8. **No cache**: the lists are answered with `Cache-Control: no-store` today and stay uncached.
9. **ADR-007 addendum** (in the story's PR): PostgreSQL full-text search is the search engine; Elasticsearch only on the triggers named in the 2026-10-05 addendum; the 100 000-award measurement of AC-1.8 as evidence.

Proposed deviations and assumptions:

1. The roadmap has no Feature 5.4 section (tracker deviation 1); scope and estimate come from ADR-007 and the tracker row (5 points, one story).
2. The name match (AC-1.6) goes beyond the ADR-007 addendum's field list; it reuses an existing index and an existing rule.
3. English stemming is not used (decision 1); "studies" finds "study" only by trigram. Recorded as a thesis limitation.
4. The reviewer queue and document contents are not searched (§2 Out); BRD §4 unified search stays partly open.
5. BRD §5.2 names 1M+ award records; the speed is proven at 100 000 (one university, ADR-022) and the 1M figure is a thesis limitation.
6. V005's `ftidx_awards_title` and `ftidx_awards_title_uk` are dropped (never used by a query; superseded); `trgm_awards_title` stays for `DuplicateFinder`.

## 8. Contract

### 8.1 OpenAPI stubs

Written to `docs/api/openapi.yml` in this PRD's PR; the new parameter carries `x-status: planned` and the story in its `description`.

| Operation | Request | Response | Problems |
|-----------|---------|----------|----------|
| `GET /awards` (`listAwards`) | + query `q` (`SearchQueryParam`) | 200 `AwardPage` (unchanged) | + 400 `invalid-parameter` (`q`); 401; 403 |
| `GET /achievements` (`listAchievements`) | + `q` | 200 `AchievementPage` (unchanged) | 400 `invalid-parameter` (+ `q`); 401; 404 |
| `GET /public/achievements` (`listPublicAchievements`) | + `q` | 200 `AchievementPage` (unchanged) | 400 `invalid-parameter` (+ `q`); 404; 429 |

`SearchQueryParam`: `name: q`, `in: query`, `schema: { type: string, maxLength: 200 }`, description: words of the titles, awarding organisation and descriptions (prefix, Ukrainian and English), typo-tolerant on the titles; on the achievements lists also a fragment of the owner's name; ignored under 2 characters; results in relevance order while searching.

### 8.2 Migration outlines

`V047__award_search.sql` (5.4.1):

| Object | Definition |
|--------|------------|
| `awards.search_vector` | `tsvector` NOT NULL, `GENERATED ALWAYS AS (setweight(to_tsvector('simple', <apostrophes removed>(coalesce(title,'') || ' ' || coalesce(title_uk,''))), 'A') || setweight(… awarding_organization …, 'B') || setweight(… description || ' ' || description_uk …, 'C')) STORED` |
| `awards.search_text` | `text` NOT NULL, `GENERATED ALWAYS AS (lower(<apostrophes removed>(coalesce(title,'') || ' ' || coalesce(title_uk,'') || ' ' || coalesce(awarding_organization,'')))) STORED` |
| `idx_awards_search` | GIN on `search_vector` |
| `trgm_awards_search` | GIN on `search_text gin_trgm_ops` |
| Dropped | `ftidx_awards_title`, `ftidx_awards_title_uk` |

Apostrophes removed with `translate(…, '''’ʼ`', '')` (immutable). Adding the stored columns rewrites `awards` once; seconds at 100 000 rows.

### 8.3 Properties

None; the limits of §7.7 and the 0.5 similarity are constants of `SearchTerms`.

## 9. Test plan

| AC | Unit | IT | FT (REST-assured) | E2E (Playwright) |
|----|------|----|-------------------|------------------|
| 1.1 | | | own list with `q` and filters, all statuses | |
| 1.2 | | | visibility, `DELETED` owner, unit award, public list, with each filter | |
| 1.3 | `SearchTerms` words, stems, apostrophes | V047 columns over the fixture texts | the seven searches | |
| 1.4 | | | typo searches, order after full-text | |
| 1.5 | | | title above description, paging over 3 pages | |
| 1.6 | | | name on `/achievements`, not on `/awards`, not unit awards | |
| 1.7 | `SearchTerms` limits, syntax characters | | 201 characters, syntax, `??` on all three | |
| 1.8 | | 100 000 rows via `generate_series`, timing and `EXPLAIN` | | |
| 1.9 | | edit, approve, erase then search; indexes dropped | | |
| 1.10 | `award-list` debounce, minimum, clear, empty text | | | search own draft by a fresh account (`registerAndVerify`) |
| 1.11 | `achievement-list` `q` in `readQuery`/`writeQuery` | | | search, reload, back on `/achievements` and the public page |
| 1.12 | | | | screen check of both pages; axe in the e2e spec |

## 10. Manual verification

Draft; expected texts are the planned wording and are checked against the merged i18n files at the feature validation.

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`; backend `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`; frontend `npm start` on `http://localhost:4200`. psql `docker compose exec postgres psql -U postgres award_monitoring`; Swagger `http://localhost:8080/swagger-ui.html`. Seed accounts (password `Passw0rd-demo`, `@chnu.edu.ua`): `lecturer.fmi` (user 966, «Оксана Викладач»), `employee.fmi` (user 8), `dean.fmi`, `admin`. Fixture awards of `lecturer.fmi`, inserted once (the generated columns fill themselves):

```sql
insert into awards (user_id, organization_id, category_id, title, title_uk, description_uk, awarding_organization,
                    award_date, status, visibility)
select 966, u.organization_id, (select min(category_id) from award_categories), t.title, t.title_uk, t.descr,
       t.org, date '2025-05-20', t.status, t.vis
from users u, (values
  ('MV54 Best Paper Award', 'MV54 Премія імені Тараса Шевченка', 'За п’ять років досліджень', 'Horizon Europe', 'APPROVED', 'PUBLIC'),
  ('MV54 Teaching grant', 'MV54 Грант викладача', null, 'Fulbright', 'APPROVED', 'UNIVERSITY'),
  ('MV54 Private medal', 'MV54 Медаль приватна', null, 'Horizon Europe', 'APPROVED', 'PRIVATE'),
  (null, 'MV54 Чернетка премії', null, null, 'DRAFT', 'PRIVATE')) as t(title, title_uk, descr, org, status, vis)
where u.user_id = 966;
```

1. psql: `select title_uk, search_vector from awards where title_uk like 'MV54%';`. Expected: four rows, `search_vector` holds `премія`, `шевченка`, `horizon`, `пять` (apostrophe removed), weights A/B/C. `\d awards` lists `idx_awards_search`, `trgm_awards_search` and no `ftidx_awards_title*`. (AC-1.9)
2. As `employee.fmi`, Swagger `GET /achievements?q=премії`. Expected: 200 with «MV54 Премія імені Тараса Шевченка» (stem `прем`); not the private medal, not the draft. (AC-1.2, 1.3)
3. Same with `q=Шевченко`, `q=best pap`, `q=horizon`, `q=пʼять`, `q=п'ять`. Expected: each finds the prize; `horizon` finds the prize but not the private medal. (AC-1.3)
4. `q=Horizn`, `q=Horrizon`, `q=Шевченкo` (Latin `o`). Expected: the prize found each time (trigram); with `q=MV54 horizon` the prize is the only match. psql `select word_similarity('horizn', search_text) from awards where title like 'MV54 Best%';` ≥ 0.5. (AC-1.4)
5. `GET /achievements?q=виклад`. Expected: the prize and the teaching grant (shared by «Оксана Викладач»), not the private medal; `GET /public/achievements?q=виклад` (no token): the prize only. (AC-1.2, 1.6)
6. `GET /achievements?q=MV54&year=2025&recipient=PERSON`, then `&year=2024`. Expected: the two shared awards, then an empty page. (AC-1.2)
7. As `lecturer.fmi`, `GET /awards?q=премії`. Expected: the prize, the private medal is absent (title does not match), the draft «MV54 Чернетка премії» present; `&status=DRAFT` leaves the draft alone. As `employee.fmi`, `GET /awards?q=виклад`: none of Оксана's awards. (AC-1.1, 1.6)
8. `GET /achievements?q=` + 201 × `a`. Expected: 400 `urn:awards:problem:invalid-parameter` naming `q`. `q='; drop table awards; --`, `q=a & b | !c:*`: 200. `q=??`: empty page. `q=a`: the list as without `q`. (AC-1.7)
9. Open `http://localhost:4200/awards` as `lecturer.fmi`, type `премії` into «Search my awards». Expected: after a pause the prize and the draft; choose status "Draft": the draft only; clear the field: the full list with the Draft filter. Type `zzzz`: «No awards match your search.». (AC-1.10)
10. `http://localhost:4200/achievements` as `employee.fmi`, type `horizon`. Expected: the prize; the address shows `?q=horizon`; reload keeps the field and the result; Back returns to the list without `q`. (AC-1.11)
11. Open `http://localhost:4200/public/achievements?q=виклад` in a private window. Expected: the field holds `виклад`, the prize only. Type `qqqq`: «No achievements match these filters yet.». (AC-1.11)
12. Screen check of `/awards` and `/achievements` at 360 and 1280 px, uk/en, light/dark. Expected: labelled field, clear button named, no page-level horizontal scroll, no serious axe finding, no raw translation key after switching language without reload. (AC-1.12)
13. Run the 5.4.1 IT of AC-1.8 and note the two timings. Expected: under 1 s each; plan with `idx_awards_search`. (AC-1.8)

### Detours

14. Direct URL `http://localhost:4200/units/9/achievements?q=премії` as `dean.fmi`. Expected: the unit page with the field filled and the prize listed only if the lecturer's department is under unit 9. (AC-1.11)
15. Backend restart mid-search: with step 10's result on screen, stop the backend, type another word. Expected: the existing list failure state with Retry; after the restart, Retry runs the search with the current text. (AC-1.11)
16. Token expiry on reload: on `/achievements?q=horizon`, expire the session (wait past the access-token lifetime, or delete the token in DevTools) and reload. Expected: sign-in, then back on `/achievements?q=horizon` with the result. (AC-1.11)
17. Second browser as `lecturer.fmi`: rename the draft to «MV54 Чернетка гранту» and save; in the first browser search `гранту` on `/awards`. Expected: the renamed draft found at once, `премії` no longer finds it. (AC-1.9)
18. Revoked mid-flow (visibility): `employee.fmi` has `?q=MV54` open on `/achievements` with the teaching grant listed; `lecturer.fmi` sets it to private on its detail page. Expected: the employee's next page change or search drops it; opening the stale card answers the existing not-found state. (AC-1.2)
19. Revoked mid-flow (account): `admin` suspends `lecturer.fmi` while `employee.fmi` has `?q=виклад` open. Expected: the next search returns none of her awards; after reactivation they return. (AC-1.2)
20. Back after a search: on `/achievements`, search `horizon`, open the prize, press Back. Expected: the list with `?q=horizon` and the same result, not the unfiltered list. (AC-1.11)
21. A text under 2 characters on `/awards` (`п`). Expected: no request carries `q`; the list as without a search. (AC-1.10)
22. Clean-up: `delete from awards where title_uk like 'MV54%';`, then `GET /achievements?q=MV54`. Expected: an empty page (the search columns went with the rows). (AC-1.9)

No action buttons are added and no role is checked beyond the existing list rules, so the "hidden in state" detours do not apply; the revoked-mid-flow detours cover the two rules the lists depend on (visibility, account status).

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| The planner skips the GIN indexes when the search is combined with the list rule and the order | Medium | Medium | AC-1.8 checks the plan at 100 000 rows; fallback: a `MATERIALIZED` CTE of matching ids first |
| The query-side stem over-matches short Ukrainian words | Medium | Low | Stem only for 5+ characters, kept at 4; relevance order puts title matches first |
| Hibernate tries to write the generated columns | Low | Medium | Not mapped for writing; AC-1.9 IT edits and approves through the API |
| Table rewrite of V047 on the demo server | Low | Low | Seconds at the demo size; migration runs at start-up before traffic |
| Calendar (deployment mid-November) | Medium | High | 5.4.1 is the cut line of Epic 5; dropping it leaves the lists as they are |

## 12. Definition of Done

- Every AC proven at the layers of §9; `.\tools\gate.ps1` green; Playwright for both pages
- Screen check of `/awards` and `/achievements`
- Security review (query built from user input, public endpoint)
- `openapi.yml` without `x-status: planned` on `SearchQueryParam`; DATA_DICTIONARY §2.1 (columns, indexes, dropped indexes); ADR-007 addendum; user guide (search on "My awards" and achievements); CHANGELOG `[Unreleased]`; tracker and BACKLOG
- Manual verification (§10) run and recorded; feature validated
