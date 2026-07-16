ALTER TABLE questions ADD COLUMN IF NOT EXISTS ui_step integer NOT NULL DEFAULT 1;

ALTER TABLE questions
    ADD CONSTRAINT chk_questions_ui_step CHECK (ui_step BETWEEN 1 AND 3);
