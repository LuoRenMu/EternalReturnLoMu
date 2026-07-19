import cn.luorenmu.ConfigFile
import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.common.util.NickNameUtil
import cn.luorenmu.repository.StatisticsRepository
import cn.luorenmu.repository.table.NicknameQueries
import org.ktorm.dsl.from
import org.ktorm.dsl.map
import org.ktorm.dsl.select

/**
 *
 * @author LoMu
 * Date 2025/11/20 00:03
 */
fun main() {
    ConfigFile.initConfig()
    println(ConfigFile.config.postgres)
    ConfigFile.config.postgres.host="192.168.1.104"
    ConfigFile.config.postgres.port=5432
    ConfigFile.config.postgres.database="bot_db"
    ConfigFile.config.postgres.user="lomu"
    ConfigFile.config.postgres.password="10201020"
    val dbManager = DatabaseManager()
    // 获取 nickname_queries 表所有名称并验证
    val db = dbManager.database ?: return
    val nicknames = db.from(NicknameQueries)
        .select(NicknameQueries.nickname)
        .map { it[NicknameQueries.nickname] ?: "" }

    println("共 ${nicknames.size} 条昵称记录：")
    nicknames.forEach { nickname ->
        if (!NickNameUtil.isValidNickname(nickname)){
            println("  $nickname -> isValid=${NickNameUtil.isValidNickname(nickname)}")
        }


    }
}
