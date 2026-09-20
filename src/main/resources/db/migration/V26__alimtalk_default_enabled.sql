-- Preserve explicit existing opt-outs; change only the default for new preferences.
ALTER TABLE user_preferences ALTER COLUMN alimtalk_enabled SET DEFAULT TRUE;
