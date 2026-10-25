CREATE TABLE IF NOT EXISTS test_bili_live (
 group_id TEXT NOT NULL, room_id BIGINT NOT NULL, live_status INTEGER NOT NULL,
 live_session TEXT NOT NULL, PRIMARY KEY(group_id,room_id));
CREATE TABLE IF NOT EXISTS bili_live_claims (
 sender_id TEXT PRIMARY KEY NOT NULL);
CREATE TABLE IF NOT EXISTS bili_live_bindings (
 group_id TEXT PRIMARY KEY NOT NULL, room_id BIGINT NOT NULL, sender_id TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS bili_live_bindings_sender ON bili_live_bindings(sender_id);
CREATE TABLE IF NOT EXISTS bili_live_ended (
 group_id TEXT NOT NULL, room_id BIGINT NOT NULL, live_session TEXT NOT NULL,
 PRIMARY KEY(group_id,room_id,live_session));
