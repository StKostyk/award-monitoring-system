-- V031__review_templates.sql
-- Description: Reusable reviewer comments per decision, in Ukrainian and English, with a starting set
-- Author: Stefan Kostyk
-- Date: 2026-10-08

-- ============================================================================
-- REVIEW_TEMPLATES
-- ============================================================================
-- Ready-made comments a reviewer picks in the decision dialog; the chosen
-- text fills the comment, which the reviewer may still edit. Reference data
-- maintained by migrations; inactive rows are kept but no longer offered.
-- ============================================================================

CREATE TABLE review_templates (
    template_id BIGSERIAL,
    decision VARCHAR(10) NOT NULL,
    title_uk VARCHAR(120) NOT NULL,
    title_en VARCHAR(120),
    body_uk VARCHAR(2000) NOT NULL,
    body_en VARCHAR(2000),
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_review_templates PRIMARY KEY (template_id),
    CONSTRAINT ck_review_templates_decision CHECK (decision IN ('APPROVE', 'REJECT', 'RETURN', 'ESCALATE')),
    CONSTRAINT ck_review_templates_title_en CHECK ((title_en IS NULL) = (body_en IS NULL))
);

CREATE INDEX idx_review_templates_decision ON review_templates(decision, sort_order) WHERE active;

COMMENT ON TABLE review_templates IS 'Ready-made reviewer comments per decision (uk with optional en)';
COMMENT ON COLUMN review_templates.decision IS 'APPROVE, REJECT, RETURN or ESCALATE: the decision the template is offered for';
COMMENT ON COLUMN review_templates.title_uk IS 'Name shown in the template list, Ukrainian';
COMMENT ON COLUMN review_templates.title_en IS 'Name shown in the template list, English; empty falls back to Ukrainian';
COMMENT ON COLUMN review_templates.body_uk IS 'Comment text filled in, Ukrainian';
COMMENT ON COLUMN review_templates.body_en IS 'Comment text filled in, English; empty falls back to Ukrainian';
COMMENT ON COLUMN review_templates.sort_order IS 'Position in the list, ascending';
COMMENT ON COLUMN review_templates.active IS 'False when the template is no longer offered';
COMMENT ON INDEX idx_review_templates_decision IS 'Active templates of a decision in list order';

CREATE TRIGGER trg_review_templates_updated_at
    BEFORE UPDATE ON review_templates
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

INSERT INTO review_templates (decision, sort_order, title_uk, title_en, body_uk, body_en) VALUES
    ('RETURN', 10, 'Немає скану сертифіката', 'Certificate scan missing',
     'Додайте скан сертифіката або диплома, що підтверджує нагороду, і подайте повторно.',
     'Please attach a scan of the certificate or diploma that confirms the award and submit it again.'),
    ('RETURN', 20, 'Невірна категорія', 'Wrong category',
     'Категорія нагороди не відповідає її рівню. Оберіть правильну категорію і подайте повторно.',
     'The award category does not match its level. Please choose the right category and submit it again.'),
    ('RETURN', 30, 'Нечитабельний скан', 'Unreadable scan',
     'Скан документа нечитабельний. Додайте чіткішу копію і подайте повторно.',
     'The document scan cannot be read. Please attach a clearer copy and submit it again.'),
    ('REJECT', 10, 'Не підтверджено документами', 'Not supported by documents',
     'Нагороду не підтверджено документами, тому її не може бути затверджено.',
     'The award is not supported by documents, so it cannot be approved.'),
    ('REJECT', 20, 'Дублікат', 'Duplicate',
     'Ця нагорода вже зареєстрована в системі.',
     'This award is already recorded in the system.'),
    ('ESCALATE', 10, 'Потребує рішення вищого рівня', 'Needs a higher-level decision',
     'Рішення щодо цієї нагороди виходить за межі моїх повноважень; передаю на вищий рівень.',
     'The decision on this award is beyond my authority; passing it to the next level.'),
    ('APPROVE', 10, 'Підтверджено', 'Confirmed',
     'Нагороду перевірено і підтверджено.',
     'The award has been checked and confirmed.');
