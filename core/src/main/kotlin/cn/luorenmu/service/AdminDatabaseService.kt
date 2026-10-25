package cn.luorenmu.service

import cn.luorenmu.common.util.DatabaseManager
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.serialization.Serializable
import org.ktorm.dsl.*
import org.ktorm.expression.ArgumentExpression
import org.ktorm.expression.FunctionExpression
import org.ktorm.schema.*
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.Duration
import java.util.Locale

/** Runtime table metadata is trusted only after validation; values and identifiers go through Ktorm. */
class AdminDatabaseService(private val databaseManager: DatabaseManager) {
    private val descriptions = Caffeine.newBuilder().maximumSize(128).expireAfterWrite(Duration.ofSeconds(60))
        .build<Pair<String?,String>,TableDescription>()

    fun tables(): List<AdminTableSummary> {
        descriptions.invalidateAll()
        val database = databaseManager.database // Resolve any fallback before selecting the schema.
        val schema = databaseManager.schema()
        val names = databaseManager.useConnection { connection ->
            connection.metaData.getTables(null,schema,"%",arrayOf("TABLE")).use { rows ->
                buildList { while (rows.next()) add(rows.getString("TABLE_NAME")) }.sorted()
            }
        }
        if (names.isEmpty()) return emptyList()
        val queries = names.map { name ->
            database.from(DynamicTable(name,schema)).select(ArgumentExpression(name,VarcharSqlType).aliased("table_name"),count().aliased("row_count"))
        }
        // Preserve one round trip for all table totals instead of an N+1 metadata refresh.
        return queries.reduce { query,next -> query.unionAll(next) }.map { AdminTableSummary(checkNotNull(it.getString(1)),it.getLong(2)) }
    }

    fun page(tableName: String, limit: Int, offset: Int, filterColumn: String? = null, filter: String? = null,
             sortColumn: String? = null, sortDirection: String = "asc"): AdminTablePage {
        val description = databaseManager.useConnection { describe(it,tableName) }
        val search = filter?.trim()?.takeIf(String::isNotEmpty)
        val searched = if (search != null) {
            require(!filterColumn.isNullOrBlank()) { "请选择查询字段" }
            description.columns.firstOrNull { it.name == filterColumn && it.jdbcType in TEXT_TYPES }
                ?: throw IllegalArgumentException("不支持查询的字段: $filterColumn")
        } else null
        val sorted = sortColumn?.takeIf(String::isNotBlank)?.let { column ->
            description.columns.firstOrNull { it.name == column && it.jdbcType in INTEGER_TYPES }
                ?: throw IllegalArgumentException("不支持排序的字段: $column")
        }
        require(sortDirection.equals("asc",true) || sortDirection.equals("desc",true)) { "排序方向无效" }
        val database = databaseManager.database
        val table = DynamicTable(description.name,databaseManager.schema(),description.columns)
        var query = database.from(table).select(table.columns)
        if (searched != null && search != null) {
            val lower = FunctionExpression("lower",listOf(table.fields.getValue(searched.name).asExpression()),VarcharSqlType)
            // Literal substring search (%, _, ! remain literal), expressed as a dialect SQL function.
            val function = if (databaseManager.backend == cn.luorenmu.common.util.DatabaseBackend.SQLITE) "instr" else "strpos"
            val position = FunctionExpression(function,listOf(lower,ArgumentExpression(search.lowercase(Locale.ROOT),VarcharSqlType)),IntSqlType)
            query = query.where { position greater 0 }
        }
        val ordering = if (sorted != null) {
            listOf(if (sortDirection.equals("asc",true)) table.fields.getValue(sorted.name).asc() else table.fields.getValue(sorted.name).desc()) +
                description.primaryKeys.filter { it != sorted.name }.map { table.fields.getValue(it).desc() }
        } else description.primaryKeys.take(1).map { table.fields.getValue(it).desc() }
        val rows = query.orderBy(ordering).limit(offset,limit).map { row ->
            description.columns.associate { column -> column.name to row[table.fields.getValue(column.name)]?.toString() }
        }
        val total = if (rows.size < limit && (rows.isNotEmpty() || offset == 0)) offset.toLong()+rows.size else {
            val counting = database.from(table).select(count())
            val filtered = (query.expression as org.ktorm.expression.SelectExpression).where
            (if (filtered != null) counting.where { filtered } else counting).map { it.getLong(1) }.single()
        }
        return AdminTablePage(description.name,description.columns,description.primaryKeys,rows,total,limit,offset)
    }

    fun update(tableName: String, request: AdminRowUpdate): Boolean {
        val description = databaseManager.useConnection { describe(it,tableName) }
        require(description.primaryKeys.isNotEmpty()) { "表 ${description.name} 没有主键，禁止在线修改" }
        require(request.keys.keys.containsAll(description.primaryKeys)) { "必须提供全部主键" }
        val editable = request.values.keys.map { name ->
            description.columns.firstOrNull { it.name == name && !it.primaryKey && !it.autoIncrement } ?: error("字段不可编辑: $name")
        }
        require(editable.isNotEmpty()) { "没有可更新字段" }
        val table = DynamicTable(description.name,databaseManager.schema(),description.columns)
        return databaseManager.database.update(table) {
            editable.forEach { column -> set(table.fields.getValue(column.name),request.values[column.name] as Any?) }
            where {
                description.primaryKeys.map { name ->
                    val column = table.fields.getValue(name)
                    request.keys[name]?.let { value -> column eq value } ?: column.isNull()
                }.reduce { left,right -> left and right }
            }
        } > 0
    }

    private fun describe(connection: Connection, requestedName: String): TableDescription {
        require(requestedName.matches(IDENTIFIER)) { "表名无效" }
        val schema = databaseManager.schema()
        return descriptions.get(schema to requestedName) { loadDescription(connection,requestedName,schema) }
    }
    private fun loadDescription(connection: Connection, name: String, schema: String?): TableDescription {
        require(connection.metaData.getTables(null,schema,name,arrayOf("TABLE")).use { it.next() }) { "表不存在: $name" }
        val keys = connection.metaData.getPrimaryKeys(null,schema,name).use { rows -> buildList { while(rows.next()) add(rows.getString("COLUMN_NAME")) } }
        val columns = connection.metaData.getColumns(null,schema,name,"%").use { rows -> buildList {
            while(rows.next()) {
                val column = rows.getString("COLUMN_NAME")
                add(AdminColumn(column,rows.getString("TYPE_NAME"),rows.getInt("DATA_TYPE"),rows.getInt("NULLABLE") != 0,
                    column in keys,rows.getString("IS_AUTOINCREMENT").equals("YES",true)))
            }
        } }
        return TableDescription(name,columns,keys)
    }
    private data class TableDescription(val name: String, val columns: List<AdminColumn>, val primaryKeys: List<String>)
    companion object {
        private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val INTEGER_TYPES = setOf(Types.TINYINT,Types.SMALLINT,Types.INTEGER,Types.BIGINT)
        private val TEXT_TYPES = setOf(Types.CHAR,Types.VARCHAR,Types.LONGVARCHAR,Types.NCHAR,Types.NVARCHAR,Types.LONGNVARCHAR)
    }
}

private class DynamicTable(name: String, schema: String?, metadata: List<AdminColumn> = emptyList()) : Table<Nothing>(name,schema=schema) {
    val fields = metadata.associate { it.name to registerColumn(it.name,AdminValueType(it.jdbcType,it.type)) }
}
private class AdminValueType(private val jdbc: Int, name: String) : SqlType<Any>(jdbc,name) {
    override fun doGetResult(rs: ResultSet,index: Int): Any? = rs.getObject(index)
    override fun doSetParameter(ps: PreparedStatement,index: Int,parameter: Any) {
        val value = parameter.toString()
        when(jdbc) {
            Types.TINYINT,Types.SMALLINT,Types.INTEGER -> ps.setInt(index,value.toInt())
            Types.BIGINT -> ps.setLong(index,value.toLong())
            Types.BOOLEAN,Types.BIT -> ps.setBoolean(index,value.toBooleanStrict())
            Types.NUMERIC,Types.DECIMAL -> ps.setBigDecimal(index,BigDecimal(value))
            Types.FLOAT,Types.REAL,Types.DOUBLE -> ps.setDouble(index,value.toDouble())
            Types.DATE -> ps.setDate(index,java.sql.Date.valueOf(value))
            Types.TIMESTAMP,Types.TIMESTAMP_WITH_TIMEZONE -> ps.setTimestamp(index,java.sql.Timestamp.valueOf(value.replace('T',' ')))
            else -> ps.setString(index,value)
        }
    }
}

@Serializable
data class AdminTableSummary(val name: String, val rowCount: Long)

@Serializable
data class AdminColumn(
    val name: String,
    val type: String,
    val jdbcType: Int,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val autoIncrement: Boolean,
)

@Serializable
data class AdminTablePage(
    val table: String,
    val columns: List<AdminColumn>,
    val primaryKeys: List<String>,
    val rows: List<Map<String, String?>>,
    val total: Long,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class AdminRowUpdate(
    val keys: Map<String, String?>,
    val values: Map<String, String?>,
)
