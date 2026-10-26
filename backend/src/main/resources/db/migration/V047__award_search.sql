-- V047__award_search.sql
-- Description: Full-text award search (PRD 5.4, story 5.4.1): generated search columns over the titles, the awarding
--              organisation and the descriptions, their GIN indexes, and the unused V005 title indexes dropped.

ALTER TABLE awards
    ADD COLUMN search_vector TSVECTOR NOT NULL GENERATED ALWAYS AS (
        setweight(to_tsvector('simple',
            translate(coalesce(title, '') || ' ' || coalesce(title_uk, ''), '''’ʼ`', '')), 'A')
        || setweight(to_tsvector('simple', translate(coalesce(awarding_organization, ''), '''’ʼ`', '')), 'B')
        || setweight(to_tsvector('simple',
            translate(coalesce(description, '') || ' ' || coalesce(description_uk, ''), '''’ʼ`', '')), 'C')
    ) STORED,
    ADD COLUMN search_text TEXT NOT NULL GENERATED ALWAYS AS (
        lower(translate(coalesce(title, '') || ' ' || coalesce(title_uk, '') || ' '
            || coalesce(awarding_organization, ''), '''’ʼ`', ''))
    ) STORED;

CREATE INDEX idx_awards_search ON awards USING GIN (search_vector);
CREATE INDEX trgm_awards_search ON awards USING GIN (search_text gin_trgm_ops);

DROP INDEX ftidx_awards_title;
DROP INDEX ftidx_awards_title_uk;

COMMENT ON COLUMN awards.search_vector IS 'Generated: words of the titles (A), awarding organisation (B) and '
    'descriptions (C), simple configuration, apostrophes removed';
COMMENT ON COLUMN awards.search_text IS 'Generated: lower-cased titles and awarding organisation for the trigram '
    'typo match, apostrophes removed';
