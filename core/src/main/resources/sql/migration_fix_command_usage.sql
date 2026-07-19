-- 修复 command_usage 表的字段长度限制问题
-- 将可能超过 255 字符的字段改为 TEXT 类型

ALTER TABLE command_usage 
    ALTER COLUMN nickname TYPE TEXT,
    ALTER COLUMN sender_id TYPE TEXT;

-- 如果表不存在，使用完整的创建语句（用于新部署）
CREATE TABLE IF NOT EXISTS command_usage (
    id BIGSERIAL PRIMARY KEY,
    command_name VARCHAR(255) NOT NULL,
    nickname TEXT,
    group_id VARCHAR(50),
    sender_id TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);
