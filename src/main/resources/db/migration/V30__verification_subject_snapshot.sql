ALTER TABLE user_verification_document ADD COLUMN subject_value VARCHAR(100) NULL;
ALTER TABLE user_verification_document ADD COLUMN invalidated_at DATETIME(6) NULL;
