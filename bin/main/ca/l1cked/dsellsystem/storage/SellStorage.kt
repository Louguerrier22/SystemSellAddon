package ca.l1cked.dsellsystem.storage

import org.bukkit.inventory.ItemStack
import java.util.UUID

interface SellStorage {
    fun init()
    fun close()

    /** Returns created transaction id. */
    fun recordSell(
        playerUuid: UUID,
        playerName: String,
        timestampMillis: Long,
        personalMultiplier: Double,
        paidByCategory: Map<String, Double>,
        totalPaid: Double,
    ): Long

    fun recordSellItems(txId: Long, items: List<ItemStack>)

    fun getHistory(uuid: UUID, limit: Int): List<SellTransaction>

    fun getItemHistory(uuid: UUID, limit: Int, offset: Int): List<SellItemEntry>

    fun getItemHistoryCount(uuid: UUID): Int

    fun purgeOlderThan(timestampMillis: Long)

    /**
     * Resolve a name to the most-recent UUID we saw for that name.
     * This allows /sell history <player> for offline players.
     */
    fun resolveUuidByLastKnownName(name: String): UUID?

    /** Distinct last-known names (for tab completion). */
    fun knownNames(limit: Int): List<String>
}
