-- V048__award_search_words.sql
-- Description: Award search over every typed form (story 5.4.2): the search columns are rebuilt from text in NFC
--              form with apostrophes removed and every run of characters other than letters and digits read as a
--              space, so that joined tokens (2024-2025, 3.5, chnu.edu.ua) split into the words the search text gives.

CREATE FUNCTION search_words(value TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    RETURN regexp_replace(translate(normalize(coalesce(value, ''), NFC), '''’ʼ`', ''), '[^[:alnum:]]+', ' ', 'g');

ALTER TABLE awards
    ALTER COLUMN search_vector SET EXPRESSION AS (
        setweight(to_tsvector('simple', search_words(title) || ' ' || search_words(title_uk)), 'A')
        || setweight(to_tsvector('simple', search_words(awarding_organization)), 'B')
        || setweight(to_tsvector('simple', search_words(description) || ' ' || search_words(description_uk)), 'C')
    ),
    ALTER COLUMN search_text SET EXPRESSION AS (
        lower(search_words(title) || ' ' || search_words(title_uk) || ' ' || search_words(awarding_organization))
    );

COMMENT ON FUNCTION search_words(TEXT) IS 'Search form of a text: NFC, apostrophes removed, every run of characters '
    'other than letters and digits replaced by one space';
COMMENT ON COLUMN awards.search_vector IS 'Generated: words of the titles (A), awarding organisation (B) and '
    'descriptions (C), simple configuration, over search_words()';
COMMENT ON COLUMN awards.search_text IS 'Generated: lower-cased search_words() of the titles and awarding '
    'organisation for the trigram typo match';
