CREATE TABLE IF NOT EXISTS code_push_event (code TEXT, phase TEXT, PRIMARY KEY(code,phase));
CREATE TABLE IF NOT EXISTS code_push_delivery (
 code TEXT,phase TEXT,group_id TEXT,body TEXT NOT NULL,expires_at INTEGER NOT NULL,
 state TEXT NOT NULL DEFAULT 'PENDING', PRIMARY KEY(code,phase,group_id));
