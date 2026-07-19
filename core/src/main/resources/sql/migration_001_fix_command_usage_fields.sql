-- 修复 command_usage 表字段长度问题
-- 将 nickname 改为 TEXT 类型以支持长昵称
-- 将 group_id 和 sender_id 改为 VARCHAR(50) 以支持较长的 QQ 号/群号

ALTER TABLE command_usage 
    ALTER COLUMN nickname TYPE TEXT,
    ALTER COLUMN group_id TYPE VARCHAR(50),
    ALTER COLUMN sender_id TYPE VARCHAR(50);
