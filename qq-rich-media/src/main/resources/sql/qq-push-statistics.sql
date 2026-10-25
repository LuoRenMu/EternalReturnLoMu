CREATE TABLE IF NOT EXISTS qq_push_statistics (
 push_kind TEXT NOT NULL, group_id TEXT NOT NULL, subject_id BIGINT NOT NULL,
 push_count BIGINT NOT NULL, PRIMARY KEY(push_kind,group_id,subject_id));
CREATE INDEX IF NOT EXISTS qq_push_statistics_subject ON qq_push_statistics(push_kind,subject_id);
CREATE TABLE IF NOT EXISTS qq_push_receipts (
 delivery_id TEXT PRIMARY KEY NOT NULL, push_kind TEXT NOT NULL,
 group_id TEXT NOT NULL, subject_id BIGINT NOT NULL, sent_at BIGINT NOT NULL);
