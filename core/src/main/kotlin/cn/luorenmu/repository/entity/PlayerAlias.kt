package cn.luorenmu.repository.entity

import java.time.LocalDateTime

/**
 * 玩家别名实体
 *
 * scope 控制别名可见范围：
 * - [AliasScope.GLOBAL]  全局可用
 * - [AliasScope.GROUP]   仅限指定群聊
 * - [AliasScope.PERSONAL] 仅限指定用户
 *
 * @author LoMu
 * Date 2026/6/2
 */
enum class AliasScope(val value: String) {
    GLOBAL("global"),
    GROUP("group"),
    PERSONAL("personal");

    companion object {
        fun from(value: String): AliasScope =
            entries.firstOrNull { it.value == value } ?: GROUP
    }
}

data class PlayerAlias(
    val id: Long = 0,
    val alias: String,
    val actualNickname: String,
    val scope: AliasScope = AliasScope.GROUP,
    val groupId: String? = null,
    val userId: String? = null,
    val createdBy: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
