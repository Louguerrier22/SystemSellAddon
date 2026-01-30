package ca.l1cked.dsellsystem.data

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.category.Category
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerDataManagerDB(private val plugin: DSellSystemPlugin) {
    private val cache: MutableMap<UUID, PlayerData> = ConcurrentHashMap()

    fun getPlayerData(uuid: UUID): PlayerData {
        val cached = cache[uuid]
        if (cached != null) {
            return cached
        }
        val data = loadPlayerData(uuid)
        cache[uuid] = data
        return data
    }

    private fun loadPlayerData(uuid: UUID): PlayerData {
        val data = PlayerData(uuid)
        val sql = "SELECT category, progress, multiplier FROM player_data WHERE uuid = ?"
        try {
            plugin.databaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val categoryName = rs.getString("category")
                        val progress = rs.getDouble("progress")
                        val multiplier = rs.getDouble("multiplier")
                        try {
                            val category = Category.valueOf(categoryName)
                            data.setProgress(category, progress)
                            data.setMultiplier(category, multiplier)
                        } catch (e: IllegalArgumentException) {
                            plugin.logger.warning("Unknown category $categoryName in database for $uuid")
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to load player data for $uuid")
            e.printStackTrace()
        }
        return data
    }

    fun savePlayerData(uuid: UUID) {
        val data = cache[uuid] ?: return
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            savePlayerDataSync(uuid, data)
        })
    }

    private fun savePlayerDataSync(uuid: UUID, data: PlayerData) {
        val sql = "INSERT OR REPLACE INTO player_data (uuid, category, progress, multiplier) VALUES (?, ?, ?, ?)"
        try {
            plugin.databaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    for (category in Category.values()) {
                        val progress = data.getProgress(category)
                        val multiplier = data.getMultiplier(category)
                        stmt.setString(1, uuid.toString())
                        stmt.setString(2, category.name)
                        stmt.setDouble(3, progress)
                        stmt.setDouble(4, multiplier)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to save player data for $uuid")
            e.printStackTrace()
        }
    }

    fun saveAllData() {
        plugin.logger.info("Saving all player data to database...")
        for ((uuid, data) in cache) {
            savePlayerDataSync(uuid, data)
        }
        plugin.logger.info("Saved ${cache.size} players to database.")
    }

    fun unloadPlayer(uuid: UUID) {
        savePlayerData(uuid)
        cache.remove(uuid)
    }
}
