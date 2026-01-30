package ca.l1cked.dsellsystem.command

import ca.l1cked.dsellsystem.storage.SellItemEntry
import ca.l1cked.dsellsystem.storage.SellStorage
import ca.l1cked.dsellsystem.storage.SellTransaction
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HistoryService(
    private val economy: Economy,
    private val storage: SellStorage,
    private val pageSize: Int,
    private val breakdownMaxCategories: Int,
) {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    fun historyFor(player: Player): List<SellTransaction> {
        return storage.getHistory(player.uniqueId, pageSize)
    }

    fun historyForName(name: String): List<SellTransaction> {
        val online = Bukkit.getPlayerExact(name)
        if (online != null) {
            return storage.getHistory(online.uniqueId, pageSize)
        }
        val uuid = storage.resolveUuidByLastKnownName(name) ?: return emptyList()
        return storage.getHistory(uuid, pageSize)
    }

    fun itemHistoryFor(uuid: java.util.UUID, limit: Int, offset: Int): List<SellItemEntry> {
        return storage.getItemHistory(uuid, limit, offset)
    }

    fun itemHistoryForName(name: String, limit: Int, offset: Int): List<SellItemEntry> {
        val online = Bukkit.getPlayerExact(name)
        if (online != null) {
            return storage.getItemHistory(online.uniqueId, limit, offset)
        }
        val uuid = storage.resolveUuidByLastKnownName(name) ?: return emptyList()
        return storage.getItemHistory(uuid, limit, offset)
    }

    fun itemHistoryCount(uuid: java.util.UUID): Int = storage.getItemHistoryCount(uuid)

    fun itemHistoryCountForName(name: String): Int {
        val online = Bukkit.getPlayerExact(name)
        if (online != null) return storage.getItemHistoryCount(online.uniqueId)
        val uuid = storage.resolveUuidByLastKnownName(name) ?: return 0
        return storage.getItemHistoryCount(uuid)
    }

    fun formatLine(tx: SellTransaction): String {
        val whenStr = formatter.format(Instant.ofEpochMilli(tx.timestampMillis))
        val total = economy.format(tx.totalPaid)
        val mult = trimMultiplier(tx.personalMultiplier)
        val breakdown = tx.paidByCategory.entries
            .sortedByDescending { it.value }
            .take(breakdownMaxCategories)
            .joinToString(", ") { (cat, amount) -> "$cat: ${economy.format(amount)}" }
        return "$whenStr: $total (x$mult) [$breakdown]"
    }

    fun formatTimestamp(ts: Long): String = formatter.format(Instant.ofEpochMilli(ts))

    fun suggestions(prefix: String, limit: Int = 50): List<String> {
        val lower = prefix.lowercase()
        val online = Bukkit.getOnlinePlayers().map { it.name }
        val known = storage.knownNames(limit = 200)
        return (online + known)
            .distinct()
            .filter { it.lowercase().startsWith(lower) }
            .take(limit)
    }

    private fun trimMultiplier(value: Double): String {
        val str = String.format("%.2f", value)
        return str.trimEnd('0').trimEnd('.')
    }
}
