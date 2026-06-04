CREATE TABLE IF NOT EXISTS command_usage (
    id BIGSERIAL PRIMARY KEY,
    command_name VARCHAR(255) NOT NULL,
    nickname VARCHAR(255),
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
