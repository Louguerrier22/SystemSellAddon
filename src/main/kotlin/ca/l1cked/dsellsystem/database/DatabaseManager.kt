package ca.l1cked.dsellsystem.database

import ca.l1cked.dsellsystem.DSellSystemPlugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseManager(private val plugin: DSellSystemPlugin) {
    private var connection: Connection? = null
    private val lock = Any()

    fun connect() {
        val folder = plugin.dataFolder
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val file = File(folder, "data.db")
        try {
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
            plugin.logger.info("Connected to SQLite database successfully!")
            createTables()
        } catch (e: ClassNotFoundException) {
            plugin.logger.severe("SQLite JDBC driver not found!")
            e.printStackTrace()
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to connect to database!")
            e.printStackTrace()
        }
    }

    private fun createTables() {
        val sql = "CREATE TABLE IF NOT EXISTS player_data (" +
            "uuid TEXT NOT NULL," +
            "category TEXT NOT NULL," +
            "progress REAL NOT NULL," +
            "multiplier REAL NOT NULL," +
            "PRIMARY KEY (uuid, category))"
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
