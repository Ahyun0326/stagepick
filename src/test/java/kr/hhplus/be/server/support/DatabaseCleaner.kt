package kr.hhplus.be.server.support

import java.sql.Connection
import javax.sql.DataSource

class DatabaseCleaner(
    private val dataSource: DataSource
) {
    private var tableNames: List<String>? = null

    fun clean() {
        dataSource.connection.use { connection ->
            val tables = tableNames ?: findTableNames(connection).also {
                tableNames = it
            }

            connection.createStatement().use { statement ->
                statement.execute("SET FOREIGN_KEY_CHECKS = 0")

                try {
                    tables.forEach { tableName ->
                        statement.executeUpdate(
                            "TRUNCATE TABLE `${tableName.replace("`", "``")}`"
                        )
                    }
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1")
                }
            }
        }
    }

    private fun findTableNames(connection: Connection): List<String> {
        val sql = """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_type = 'BASE TABLE'
              AND table_name NOT IN (
                  'flyway_schema_history',
                  'databasechangelog',
                  'databasechangeloglock'
              )
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.getString("table_name"))
                    }
                }
            }
        }
    }
}