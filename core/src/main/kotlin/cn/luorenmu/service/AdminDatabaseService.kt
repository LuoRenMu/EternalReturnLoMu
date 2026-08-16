package cn.luorenmu.service

import cn.luorenmu.common.util.DatabaseManager
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class AdminDatabaseService(private val databaseManager: DatabaseManager) {
    fun tables(): List<AdminTableSummary> = databaseManager.useConnection { connection ->
        val schema = databaseManager.schema()
        connection.metaData.getTables(null, schema, "%", arrayOf("TABLE")).use { tables ->
            buildList {
                while (tables.next()) {
                    val name = tables.getString("TABLE_NAME")
                    add(AdminTableSummary(name, countRows(connection, schema, name)))
                }
            }.sortedBy(AdminTableSummary::name)
        }
    }

    fun page(tableName: String, limit: Int, offset: Int): AdminTablePage =
        databaseManager.useConnection { connection ->
            val table = describe(connection, tableName)
            val order = table.primaryKeys.firstOrNull()?.let { " ORDER BY ${quote(it)} DESC" }.orEmpty()
            val sql = "SELECT * FROM ${qualified(table.name)}$order LIMIT ? OFFSET ?"
            val rows = connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, limit)
                statement.setInt(2, offset)
                statement.executeQuery().use { result -> result.toRows(table.columns) }
            }
            AdminTablePage(table.name, table.columns, table.primaryKeys, rows, countRows(connection, databaseManager.schema(), table.name), limit, offset)
        }

    fun update(tableName: String, request: AdminRowUpdate): Boolean =
        databaseManager.useConnection { connection ->
            val table = describe(connection, tableName)
            require(table.primaryKeys.isNotEmpty()) { "表 ${table.name} 没有主键，禁止在线修改" }
            require(request.keys.keys.containsAll(table.primaryKeys)) { "必须提供全部主键" }

            val editable = request.values.keys.map { name ->
                table.columns.firstOrNull { it.name == name && !it.primaryKey && !it.autoIncrement }
                    ?: error("字段不可编辑: $name")
            }
            require(editable.isNotEmpty()) { "没有可更新字段" }
            val keys = table.primaryKeys.map { key -> table.columns.first { it.name == key } }
            val sql = buildString {
                append("UPDATE ${qualified(table.name)} SET ")
                append(editable.joinToString { "${quote(it.name)} = ?" })
                append(" WHERE ")
                append(keys.joinToString(" AND ") { "${quote(it.name)} = ?" })
            }
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                editable.forEach { column -> statement.bind(index++, column.jdbcType, request.values[column.name]) }
                keys.forEach { column -> statement.bind(index++, column.jdbcType, request.keys[column.name]) }
                statement.executeUpdate() > 0
            }
        }

    private fun describe(connection: Connection, requestedName: String): TableDescription {
        require(requestedName.matches(IDENTIFIER)) { "表名无效" }
        val schema = databaseManager.schema()
        val available = connection.metaData.getTables(null, schema, requestedName, arrayOf("TABLE")).use { it.next() }
        require(available) { "表不存在: $requestedName" }
        val primaryKeys = connection.metaData.getPrimaryKeys(null, schema, requestedName).use { result ->
            buildList { while (result.next()) add(result.getString("COLUMN_NAME")) }
        }
        val columns = connection.metaData.getColumns(null, schema, requestedName, "%").use { result ->
            buildList {
                while (result.next()) {
                    val name = result.getString("COLUMN_NAME")
                    add(
                        AdminColumn(
                            name = name,
                            type = result.getString("TYPE_NAME"),
                            jdbcType = result.getInt("DATA_TYPE"),
                            nullable = result.getInt("NULLABLE") != 0,
                            primaryKey = name in primaryKeys,
                            autoIncrement = result.getString("IS_AUTOINCREMENT").equals("YES", true),
                        )
                    )
                }
            }
        }
        return TableDescription(requestedName, columns, primaryKeys)
    }

    private fun countRows(connection: Connection, schema: String?, table: String): Long =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM ${qualified(table, schema)}").use { result ->
                result.next()
                result.getLong(1)
            }
        }

    private fun ResultSet.toRows(columns: List<AdminColumn>): List<Map<String, String?>> = buildList {
        while (next()) add(columns.associate { column -> column.name to getObject(column.name)?.toString() })
    }

    private fun PreparedStatement.bind(index: Int, jdbcType: Int, value: String?) {
        if (value == null) {
            setNull(index, jdbcType)
            return
        }
        when (jdbcType) {
            Types.TINYINT, Types.SMALLINT, Types.INTEGER -> setInt(index, value.toInt())
            Types.BIGINT -> setLong(index, value.toLong())
            Types.BOOLEAN, Types.BIT -> setBoolean(index, value.toBooleanStrict())
            Types.NUMERIC, Types.DECIMAL -> setBigDecimal(index, BigDecimal(value))
            Types.FLOAT, Types.REAL, Types.DOUBLE -> setDouble(index, value.toDouble())
            Types.DATE -> setDate(index, java.sql.Date.valueOf(value))
            Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> setTimestamp(index, java.sql.Timestamp.valueOf(value.replace('T', ' ')))
            else -> setString(index, value)
        }
    }

    private fun qualified(table: String, schema: String? = databaseManager.schema()) =
        if (schema == null) quote(table) else "${quote(schema)}.${quote(table)}"
    private fun quote(identifier: String) = "\"${identifier.replace("\"", "\"\"")}\""

    private data class TableDescription(
        val name: String,
        val columns: List<AdminColumn>,
        val primaryKeys: List<String>,
    )

    companion object {
        private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
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
