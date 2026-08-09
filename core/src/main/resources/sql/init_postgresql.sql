CREATE TABLE IF NOT EXISTS command_usage (
    id BIGSERIAL PRIMARY KEY,
    command_name VARCHAR(255) NOT NULL,
    nickname VARCHAR(255),
    group_id VARCHAR(255),
    sender_id VARCHAR(255),
    timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS nickname_queries (
    id BIGSERIAL PRIMARY KEY,
    nickname VARCHAR(255) NOT NULL UNIQUE,
    query_count BIGINT NOT NULL DEFAULT 0,
    first_query_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_query_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS player_aliases (
    id BIGSERIAL PRIMARY KEY,
    alias_name VARCHAR(255) NOT NULL,
    actual_nickname VARCHAR(255) NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'group',
    group_id VARCHAR(255),
    user_id VARCHAR(255),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS eternal_return_news (
    id BIGSERIAL PRIMARY KEY,
    article_id INTEGER NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    thumbnail_url VARCHAR(255),
    created_at TIMESTAMP,
    content_text TEXT NOT NULL DEFAULT '',
    event_start_time TIMESTAMP,
    event_end_time TIMESTAMP,
    is_game_activity BOOLEAN NOT NULL DEFAULT FALSE,
    is_redemption_code BOOLEAN NOT NULL DEFAULT FALSE,
    code VARCHAR(255),
    reward VARCHAR(255),
    note TEXT,
    start_date VARCHAR(20),
    end_date VARCHAR(20),
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);
