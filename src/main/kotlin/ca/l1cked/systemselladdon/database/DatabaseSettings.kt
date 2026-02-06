package ca.l1cked.systemselladdon.database

import org.bukkit.configuration.file.FileConfiguration
import java.io.File

enum class DatabaseType {
    SQLITE,
    MYSQL,
}

data class DatabaseSettings(
    val type: DatabaseType,
    val sqliteFile: String,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val useSsl: Boolean,
    val params: String,
) {
    fun driverClass(): String = when (type) {
        DatabaseType.SQLITE -> "org.sqlite.JDBC"
        DatabaseType.MYSQL -> "com.mysql.cj.jdbc.Driver"
    }

    fun jdbcUrl(): String = when (type) {
        DatabaseType.SQLITE -> "jdbc:sqlite:$sqliteFile"
        DatabaseType.MYSQL -> buildString {
            append("jdbc:mysql://")
            append(host)
            append(":")
            append(port)
            append("/")
            append(database)
            append("?useSSL=")
            append(useSsl)
            if (params.isNotBlank()) {
                append("&")
                append(params)
            }
        }
    }
}

object DatabaseSettingsLoader {
    fun load(config: FileConfiguration, dataFolderPath: String): DatabaseSettings {
        val typeRaw = config.getString("database.type", "sqlite")?.lowercase() ?: "sqlite"
        val type = if (typeRaw == "mysql") DatabaseType.MYSQL else DatabaseType.SQLITE
        val sqliteFile = config.getString("database.sqlite.file", "data.db") ?: "data.db"
        val host = config.getString("database.mysql.host", "localhost") ?: "localhost"
        val port = config.getInt("database.mysql.port", 3306)
        val database = config.getString("database.mysql.database", "systemselladdon") ?: "systemselladdon"
        val username = config.getString("database.mysql.username", "root") ?: "root"
        val password = config.getString("database.mysql.password", "") ?: ""
        val useSsl = config.getBoolean("database.mysql.use-ssl", false)
        val params = config.getString(
            "database.mysql.params",
            "allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8",
        ) ?: ""
        val sqlitePath = if (sqliteFile.contains(":") || sqliteFile.startsWith("/")) {
            sqliteFile
        } else {
            File(dataFolderPath, sqliteFile).absolutePath
        }

        return DatabaseSettings(
            type = type,
            sqliteFile = sqlitePath,
            host = host,
            port = port,
            database = database,
            username = username,
            password = password,
            useSsl = useSsl,
            params = params,
        )
    }
}
