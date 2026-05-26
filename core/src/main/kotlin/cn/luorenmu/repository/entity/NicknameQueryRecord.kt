package cn.luorenmu.repository.entity

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.LocalDateTime

/**
 * 昵称查询记录实体
 *
 * @author LoMu
 * Date 2026/5/1 18:37
 */
data class NicknameQueryRecord(
    @BsonId
    val id: ObjectId = ObjectId(),
    val nickname: String,
    var queryCount: Long = 0L,
    val firstQueryAt: LocalDateTime,
    var lastQueryAt: LocalDateTime,
)