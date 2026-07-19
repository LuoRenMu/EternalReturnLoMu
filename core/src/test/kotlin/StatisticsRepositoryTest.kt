import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.repository.StatisticsRepository
import java.time.LocalDateTime

/**
 * StatisticsRepository 测试（PostgreSQL + Ktorm 版本）
 * 测试前请确保 PostgreSQL 已启动且配置正确，并已执行初始化 SQL。
 *
 * 初始化 SQL（在 bot_db 中执行）：
 * ```sql
 * CREATE TABLE IF NOT EXISTS command_usage (
 *     id BIGSERIAL PRIMARY KEY,
 *     command_name VARCHAR(255) NOT NULL,
 *     nickname VARCHAR(255),
 *     timestamp TIMESTAMP NOT NULL DEFAULT NOW()
 * );
 *
 * CREATE TABLE IF NOT EXISTS nickname_queries (
 *     id BIGSERIAL PRIMARY KEY,
 *     nickname VARCHAR(255) NOT NULL UNIQUE,
 *     query_count BIGINT NOT NULL DEFAULT 0,
 *     first_query_at TIMESTAMP NOT NULL DEFAULT NOW(),
 *     last_query_at TIMESTAMP NOT NULL DEFAULT NOW()
 * );
 *
 * CREATE INDEX IF NOT EXISTS idx_command_usage_command_name ON command_usage(command_name);
 * CREATE INDEX IF NOT EXISTS idx_command_usage_timestamp ON command_usage(timestamp DESC);
 * CREATE INDEX IF NOT EXISTS idx_nickname_queries_query_count ON nickname_queries(query_count DESC);
 * ```
 *
 * @author LoMu
 * Date 2026/5/1 22:42
 */
fun main() {
    println("===== StatisticsRepository 测试开始 (PostgreSQL) =====")
    println("测试时间: ${LocalDateTime.now()}")
    println()

    // 初始化 DatabaseManager（会自动读取 ConfigFile 配置）
    val dbManager = DatabaseManager()

    if (!dbManager.isEnabled()) {
        println("⚠️ PostgreSQL 未启用，请检查配置文件中的 postgres.enabled 设置")
        println("测试终止")
        return
    }

    println("✅ PostgreSQL 已连接")
    println()

    val repository = StatisticsRepository(dbManager)

    // ===== 测试 1: 记录命令使用 =====
    println("----- 测试 1: recordCommandUsage -----")
    try {
        repository.recordCommandUsage(
            commandName = "/search",
            nickname = "测试用户A",
        )
        println("✅ 命令使用记录插入成功: /search, 测试用户A")

        repository.recordCommandUsage(
            commandName = "/search",
            nickname = "测试用户B",
        )
        println("✅ 命令使用记录插入成功: /search, 测试用户B")

        repository.recordCommandUsage(
            commandName = "/tier",
            nickname = null,
        )
        println("✅ 命令使用记录插入成功: /tier, nickname=null")
    } catch (e: Exception) {
        println("❌ 命令使用记录插入失败: ${e.message}")
        e.printStackTrace()
    }
    println()

    // ===== 测试 2: 查询命令使用统计 =====
    println("----- 测试 2: getCommandUsageStats -----")
    try {
        val allStats = repository.getCommandUsageStats(limit = 10)
        println("✅ 获取所有命令统计成功，共 ${allStats.size} 条记录")
        allStats.forEach { record ->
            println("   - 命令: ${record.commandName}, 用户: ${record.nickname ?: "匿名"}, 时间: ${record.timestamp}")
        }

        val searchStats = repository.getCommandUsageStats(commandName = "/search", limit = 10)
        println("✅ 获取 /search 命令统计成功，共 ${searchStats.size} 条记录")
    } catch (e: Exception) {
        println("❌ 获取命令统计失败: ${e.message}")
        e.printStackTrace()
    }
    println()

    // ===== 测试 3: 递增昵称查询计数 =====
    println("----- 测试 3: incrementNicknameQueryCount -----")
    try {
        repository.incrementNicknameQueryCount("测试昵称A")
        println("✅ 昵称查询计数递增成功: 测试昵称A (第1次)")

        repository.incrementNicknameQueryCount("测试昵称A")
        println("✅ 昵称查询计数递增成功: 测试昵称A (第2次)")

        repository.incrementNicknameQueryCount("测试昵称B")
        println("✅ 昵称查询计数递增成功: 测试昵称B (第1次)")
    } catch (e: Exception) {
        println("❌ 昵称查询计数递增失败: ${e.message}")
        e.printStackTrace()
    }
    println()

    // ===== 测试 4: 获取昵称查询计数 =====
    println("----- 测试 4: getNicknameQueryCount -----")
    try {
        val countA = repository.getNicknameQueryCount("测试昵称A")
        println("✅ 测试昵称A 查询次数: $countA (期望: 2)")

        val countB = repository.getNicknameQueryCount("测试昵称B")
        println("✅ 测试昵称B 查询次数: $countB (期望: 1)")

        val countC = repository.getNicknameQueryCount("不存在的昵称")
        println("✅ 不存在的昵称 查询次数: $countC (期望: 0)")
    } catch (e: Exception) {
        println("❌ 获取昵称查询计数失败: ${e.message}")
        e.printStackTrace()
    }
    println()

    // ===== 测试 5: 获取热门昵称 =====
    println("----- 测试 5: getTopQueriedNicknames -----")
    try {
        val topNicknames = repository.getTopQueriedNicknames(limit = 5)
        println("✅ 获取热门昵称成功，共 ${topNicknames.size} 条")
        topNicknames.forEachIndexed { index, record ->
            println("   #${index + 1}: ${record.nickname} - 查询 ${record.queryCount} 次")
        }
    } catch (e: Exception) {
        println("❌ 获取热门昵称失败: ${e.message}")
        e.printStackTrace()
    }
    println()

    // ===== 测试 6: 参数验证（应抛出异常） =====
    println("----- 测试 6: 参数验证 -----")
    try {
        repository.recordCommandUsage(commandName = "  ")
        println("❌ 应抛出异常但未抛出: 空白 commandName")
    } catch (e: IllegalArgumentException) {
        println("✅ 参数验证通过: 空白 commandName 抛出 IllegalArgumentException")
    }

    try {
        repository.incrementNicknameQueryCount("")
        println("❌ 应抛出异常但未抛出: 空 nickname")
    } catch (e: IllegalArgumentException) {
        println("✅ 参数验证通过: 空 nickname 抛出 IllegalArgumentException")
    }
    println()

    println("===== 测试完成 =====")
    println("测试数据已插入到 PostgreSQL，如需清理请手动删除测试数据")
}
