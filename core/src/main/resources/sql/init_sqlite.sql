CREATE TABLE IF NOT EXISTS command_usage (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    command_name TEXT NOT NULL,
    nickname TEXT,
    group_id TEXT,
    sender_id TEXT,
    timestamp TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS nickname_queries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nickname TEXT NOT NULL UNIQUE,
    query_count INTEGER NOT NULL DEFAULT 0,
    first_query_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_query_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_query_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sender_id TEXT NOT NULL,
    nickname TEXT NOT NULL,
    query_count INTEGER NOT NULL DEFAULT 0,
    first_query_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_query_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(sender_id, nickname)
);

CREATE TABLE IF NOT EXISTS player_aliases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    alias_name TEXT NOT NULL,
    actual_nickname TEXT NOT NULL,
    scope TEXT NOT NULL DEFAULT 'group',
    group_id TEXT,
    user_id TEXT,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS eternal_return_news (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    article_id INTEGER NOT NULL UNIQUE,
    title TEXT NOT NULL,
    thumbnail_url TEXT,
    created_at TEXT,
    content_text TEXT NOT NULL DEFAULT '',
    event_start_time TEXT,
    event_end_time TEXT,
    is_game_activity INTEGER NOT NULL DEFAULT 0,
    is_redemption_code INTEGER NOT NULL DEFAULT 0,
    code TEXT,
    reward TEXT,
    note TEXT,
    start_date TEXT,
    end_date TEXT,
    processed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exception_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL,
    exception_type TEXT NOT NULL,
    message TEXT NOT NULL DEFAULT '',
    context TEXT NOT NULL DEFAULT '',
    stack_trace TEXT NOT NULL,
    occurred_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
