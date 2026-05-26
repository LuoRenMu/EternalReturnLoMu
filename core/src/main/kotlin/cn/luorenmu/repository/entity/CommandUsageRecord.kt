package cn.luorenmu.repository.entity

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.LocalDateTime

/**
 * 命令使用记录实体
 *
 * @author LoMu
 * Date 2026/5/1 18:36
 */
data class CommandUsageRecord(
    @BsonId
    val id: ObjectId = ObjectId(),
    val commandName: String,
    val nickname: String? = null,
    val timestamp: LocalDateTime,
)