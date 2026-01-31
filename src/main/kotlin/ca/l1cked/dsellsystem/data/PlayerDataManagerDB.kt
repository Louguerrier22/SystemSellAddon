package ca.l1cked.dsellsystem.data

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.category.Category
import java.sql.SQLException
import java.util.EnumMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch

class PlayerDataManagerDB(private val plugin: DSellSystemPlugin) {
    private val cache: MutableMap<UUID, PlayerData> = ConcurrentHashMap()
    private val loading: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val loaded: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun getPlayerData(uuid: UUID): PlayerData {
        val cached = cache[uuid]
        if (cached != null) {
            if (!loaded.contains(uuid)) {
                queueLoad(uuid, cached)
            }
            return cached
        }
        val data = PlayerData(uuid)
        cache[uuid] = data
        queueLoad(uuid, data)
        return data
    }

    fun isLoaded(uuid: UUID): Boolean = loaded.contains(uuid)

    private fun queueLoad(uuid: UUID, target: PlayerData) {
        if (!loading.add(uuid)) return
        plugin.ioScope.launch {
            val snapshot = loadSnapshot(uuid)
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (cache[uuid] === target) {
                    applySnapshot(target, snapshot)
                    loaded.add(uuid)
                }
                loading.remove(uuid)
            })
        }
    }

    private fun loadSnapshot(uuid: UUID): Map<Category, Pair<Double, Double>> {
        val snapshot = EnumMap<Category, Pair<Double, Double>>(Category::class.java)
        val sql = "SELECT category, progress, multiplier FROM player_data WHERE uuid = ?"
        try {
            plugin.databaseManager.withConnection { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val categoryName = rs.getString("category")
                        val progress = rs.getDouble("progress")
                        val multiplier = rs.getDouble("multiplier")
                        try {
                            val category = Category.valueOf(categoryName)
                            snapshot[category] = progress to multiplier
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
        return snapshot
    }

    private fun applySnapshot(target: PlayerData, snapshot: Map<Category, Pair<Double, Double>>) {
        for ((category, values) in snapshot) {
            target.setProgress(category, values.first)
            target.setMultiplier(category, values.second)
        }
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
            plugin.databaseManager.withConnection { conn ->
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
        loaded.remove(uuid)
    }
}
