# ADR 0002: 业务数据操作统一使用 Ktorm DSL

## Status

Accepted — 2026-10-23

## Context

业务仓储混用 Ktorm 与手写 JDBC SQL，SQLite／PostgreSQL 的参数绑定、冲突处理和事务行为分散。
管理后台还需要根据数据库元数据动态查询表，不能把表名和用户输入直接拼接为业务 SQL。
用户明确选择：业务 SQL 使用 Ktorm DSL，独立保留建表和迁移脚本。

## Decision

- 生产代码中的查询、插入、条件更新、删除统一通过 Ktorm 3.6 DSL／类型化表达式构造。
- `common.database` 提供借用已有连接的 Adapter；查询不会关闭宿主连接，也不会另开连接逃离原事务。
- SQLite／PostgreSQL 方言显式选择，冲突忽略使用各自 DSL 的 `onConflict { doNothing() }`，不做先查后插。
- 事务使用 JDBC 的提交／回滚机制；SQLite 写事务保持 IMMEDIATE 模式，PostgreSQL 群级事务锁使用类型化函数表达式。
- 后台字段先经过元数据校验，再注册动态 Ktorm Table／SqlType；值仍参数化，文本查询保留字面子串匹配。
- JDBC 元数据读取、连接有效性验证不是手写业务 SQL，可以保留。
- 建表、索引、迁移和 SQLite PRAGMA 放入所属模块的 `src/main/resources/sql/`，由独立脚本执行器加载；测试 fixture 可以使用原生 SQL。

## Consequences

- 每群订阅上限、租约 CAS、权限事件顺序、直播游标和兑换码去重的原子性保持不变。
- SQLite 导入 PostgreSQL 与迁移完成标记处于同一事务；失败整体回滚，不改写旧 SQLite 文件。
- 兑换码事件与待发送记录一起提交；发送前独立持久化 ATTEMPTED，继续保持至多一次发送尝试。
- 新增 Adapter 和 SQLite 方言随 `qq-rich-media` 嵌入插件，兼容已验证的 3.0.3 宿主；不打包 Ktorm 共享接口或额外 ServiceLoader 方言声明，避免改变旧宿主的默认方言发现。core 自身的 SQL 重构仍需升级主程序才会生效。
- PostgreSQL 集成测试使用隔离实例的 `GROUP_PET_PG_TEST_URL`，创建随机 schema 并在测试后删除；未提供时明确跳过，不触碰生产库。
