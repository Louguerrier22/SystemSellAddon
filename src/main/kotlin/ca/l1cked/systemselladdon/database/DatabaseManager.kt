package ca.l1cked.systemselladdon.database

import ca.l1cked.systemselladdon.SystemSellAddonPlugin
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseManager(private val plugin: SystemSellAddonPlugin) {
    private var connection: Connection? = null
    private val lock = Any()
    private lateinit var settings: DatabaseSettings

    val type: DatabaseType
        get() = settings.type

    fun getSettings(): DatabaseSettings = settings

    fun connect() {
        settings = DatabaseSettingsLoader.load(plugin.config, plugin.dataFolder.absolutePath)
        try {
            Class.forName(settings.driverClass())
            connection = if (settings.type == DatabaseType.MYSQL) {
                DriverManager.getConnection(settings.jdbcUrl(), settings.username, settings.password)
            } else {
                DriverManager.getConnection(settings.jdbcUrl())
            }
            if (settings.type == DatabaseType.SQLITE) {
                connection?.createStatement()?.use { it.execute("PRAGMA foreign_keys = ON") }
            }
            plugin.logger.info("Connected to ${settings.type.name.lowercase()} database successfully!")
            createTables()
        } catch (e: ClassNotFoundException) {
            plugin.logger.severe("Database JDBC driver not found for ${settings.type.name.lowercase()}!")
            e.printStackTrace()
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to connect to database!")
            e.printStackTrace()
        }
    }

    private fun createTables() {
        val sql = if (settings.type == DatabaseType.MYSQL) {
            "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid VARCHAR(36) NOT NULL," +
                "category VARCHAR(32) NOT NULL," +
                "progress DOUBLE NOT NULL," +
                "multiplier DOUBLE NOT NULL," +
                "PRIMARY KEY (uuid, category)" +
                ") ENGINE=InnoDB;"
        } else {
            "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "progress REAL NOT NULL," +
                "multiplier REAL NOT NULL," +
                "PRIMARY KEY (uuid, category))"
        }
        try {
            connection?.prepareStatement(sql).use { stmt ->
                stmt?.executeUpdate()
            }
            plugin.logger.info("Database tables created successfully!")
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to create database tables!")
            e.printStackTrace()
        }
    }

    fun getPlayerDataUpsertSql(): String {
        return if (settings.type == DatabaseType.MYSQL) {
            "INSERT INTO player_data (uuid, category, progress, multiplier) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE progress = VALUES(progress), multiplier = VALUES(multiplier)"
        } else {
            "INSERT OR REPLACE INTO player_data (uuid, category, progress, multiplier) VALUES (?, ?, ?, ?)"
        }
    }

    @Throws(SQLException::class)
    fun getConnection(): Connection {
        if (connection == null || connection!!.isClosed) {
            connect()
        }
        return connection ?: throw SQLException("Connection is not available")
    }

    fun <T> withConnection(action: (Connection) -> T): T {
        synchronized(lock) {
            val conn = getConnection()
            return action(conn)
        }
    }

    fun disconnect() {
        try {
            if (connection != null && !connection!!.isClosed) {
                connection!!.close()
                plugin.logger.info("Database connection closed.")
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Error closing database connection!")
            e.printStackTrace()
        }
    }
}
