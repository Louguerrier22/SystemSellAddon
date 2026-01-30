package ca.l1cked.dsellsystem.sell

import ca.l1cked.dsellsystem.api.event.DSellPayoutEvent
import ca.l1cked.dsellsystem.api.event.DSellPayoutSummaryEvent
import ca.l1cked.dsellsystem.category.Category
import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.storage.SellStorage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import net.milkbowl.vault.economy.Economy
import org.bukkit.inventory.ItemStack
import kotlinx.coroutines.launch
import kotlin.math.round

data class SellResult(
    val transactionId: Long,
    val personalMultiplier: Double,
    /** Category id -> amount paid (personal multiplier already applied) */
    val paidByCategory: Map<String, Double>,
    /** Category id -> display */
    val categoryDisplays: Map<String, String>,
    val totalPaid: Double,
    val timestampMillis: Long,
)

sealed interface SellOutcome {
    data object NoItems : SellOutcome
    data class EconomyFailure(val errorMessage: String?) : SellOutcome
    data class Success(val result: SellResult) : SellOutcome
}

class SellService(
    private val economy: Economy,
    private val pricing: PricingService,
    private val multiplierService: MultiplierService,
    private val storage: SellStorage,
) {

    fun sellAll(player: Player): SellOutcome {
        if (!DSellSystemPlugin.instance.config.getBoolean("settings.enabled", true)) {
            return SellOutcome.EconomyFailure("disabled")
        }
        val inv = player.inventory

        val baseTotals = linkedMapOf<String, Double>()
        val displays = linkedMapOf<String, String>()
        val slotsToClear = mutableListOf<Int>()
        val soldItems = mutableListOf<ItemStack>()
        val hasDetailListeners = DSellPayoutEvent.hasListeners()
        val categoryTotals = if (hasDetailListeners) DoubleArray(Category.size) else null

        val contents = inv.contents
        for (i in contents.indices) {
            val stack = contents[i] ?: continue
            if (stack.amount <= 0) continue

            val priceEntry = pricing.priceFor(stack.type) ?: continue
            val base = priceEntry.pricePerItem * stack.amount.toDouble()
            baseTotals[priceEntry.categoryId] = (baseTotals[priceEntry.categoryId] ?: 0.0) + base
            displays[priceEntry.categoryId] = priceEntry.categoryDisplay
            slotsToClear.add(i)
            soldItems.add(stack.clone())

            if (isShulkerBox(stack.type)) {
                val (contentTotals, contentDisplays) = shulkerContentsTotals(stack)
                for ((catId, amount) in contentTotals) {
                    baseTotals[catId] = (baseTotals[catId] ?: 0.0) + amount
                }
                for ((catId, display) in contentDisplays) {
                    displays[catId] = display
                }
            }
        }

        if (baseTotals.isEmpty()) return SellOutcome.NoItems

        val multiplier = multiplierService.getPersonalMultiplier(player)
        val paidByCategory = baseTotals.mapValues { (_, v) -> roundMoney(v * multiplier) }
        val total = roundMoney(paidByCategory.values.sum())

        val response = economy.depositPlayer(player, total)
        if (!response.transactionSuccess()) {
            // If economy plugin rejects the transaction, do not remove items.
            return SellOutcome.EconomyFailure(response.errorMessage)
        }

        // Remove sold items only after successful payout.
        for (slot in slotsToClear) {
            inv.setItem(slot, null)
        }
        player.updateInventory()

        val now = System.currentTimeMillis()
        val historyEnabled = DSellSystemPlugin.instance.config.getBoolean("history.enabled", false)
        val txId = if (historyEnabled) {
            storage.recordSell(
                playerUuid = player.uniqueId,
                playerName = player.name,
                timestampMillis = now,
                personalMultiplier = multiplier,
                paidByCategory = paidByCategory,
                totalPaid = total,
            )
        } else {
            -1L
        }
        if (historyEnabled && soldItems.isNotEmpty()) {
            DSellSystemPlugin.instance.ioScope.launch {
                storage.recordSellItems(txId, soldItems)
            }
        }

        val txIdForEvents = if (historyEnabled) txId else null
        if (DSellPayoutSummaryEvent.hasListeners()) {
            Bukkit.getPluginManager().callEvent(
                DSellPayoutSummaryEvent(
                    player = player,
                    personalMultiplier = multiplier,
                    totalPaid = total,
                    transactionId = txIdForEvents,
                    timestampMillis = now,
                )
            )
        }

        if (categoryTotals != null) {
            for ((categoryKey, paid) in paidByCategory) {
                val id = Category.idForKey(categoryKey) ?: continue
                categoryTotals[id] += paid
            }
            val (ids, paid) = buildCategoryPayload(categoryTotals)
            Bukkit.getPluginManager().callEvent(
                DSellPayoutEvent(
                    player = player,
                    categoryIds = ids,
                    paidByCategory = paid,
                    personalMultiplier = multiplier,
                    totalPaid = total,
                    transactionId = txIdForEvents,
                    timestampMillis = now,
                )
            )
        }

        notifyMayorCallback(
            playerId = player.uniqueId,
            paidByCategory = paidByCategory,
            totalPaid = total,
            timestampMillis = now,
            transactionId = txIdForEvents?.toString(),
        )

        return SellOutcome.Success(
            SellResult(
                transactionId = txId,
                personalMultiplier = multiplier,
                paidByCategory = paidByCategory,
                categoryDisplays = displays,
                totalPaid = total,
                timestampMillis = now,
            )
        )
    }

    private fun roundMoney(value: Double): Double {
        // 2 decimal places, stable rounding.
        return round(value * 100.0) / 100.0
    }

    private fun notifyMayorCallback(
        playerId: java.util.UUID,
        paidByCategory: Map<String, Double>,
        totalPaid: Double,
        timestampMillis: Long,
        transactionId: String?,
    ) {
        val plugin = DSellSystemPlugin.instance
        if (!plugin.mayorBridge.isAvailable()) {
            plugin.mayorBridge.refreshProvider()
            if (!plugin.mayorBridge.isAvailable()) return
        }
        val payload = DoubleArray(Category.size + 1)
        for ((categoryKey, paid) in paidByCategory) {
            val id = Category.idForKey(categoryKey) ?: continue
            if (id in 0 until Category.size) {
                payload[id] = paid
            }
        }
        payload[Category.size] = totalPaid
        plugin.mayorBridge.notifyPayout(
            playerId = playerId,
            paidByCategory = payload,
            totalPaid = totalPaid,
            timestampMillis = timestampMillis,
            transactionId = transactionId,
        )
    }


    private fun isShulkerBox(material: org.bukkit.Material): Boolean =
        material.name.contains("SHULKER_BOX")

    private fun shulkerContents(item: org.bukkit.inventory.ItemStack): List<org.bukkit.inventory.ItemStack> {
        val meta = item.itemMeta as? org.bukkit.inventory.meta.BlockStateMeta ?: return emptyList()
        val state = meta.blockState as? org.bukkit.block.ShulkerBox ?: return emptyList()
        return state.inventory.contents.filterNotNull().filter { it.amount > 0 }
    }

    private fun shulkerContentsTotals(item: org.bukkit.inventory.ItemStack): Pair<Map<String, Double>, Map<String, String>> {
        val meta = item.itemMeta as? org.bukkit.inventory.meta.BlockStateMeta ?: return emptyMap<String, Double>() to emptyMap()
        val state = meta.blockState as? org.bukkit.block.ShulkerBox ?: return emptyMap<String, Double>() to emptyMap()
        val totals = linkedMapOf<String, Double>()
        val displays = linkedMapOf<String, String>()
        for (stack in state.inventory.contents) {
            if (stack == null || stack.amount <= 0) continue
            val priceEntry = pricing.priceFor(stack.type) ?: continue
            val base = priceEntry.pricePerItem * stack.amount.toDouble()
            totals[priceEntry.categoryId] = (totals[priceEntry.categoryId] ?: 0.0) + base
            displays[priceEntry.categoryId] = priceEntry.categoryDisplay
        }
        return totals to displays
    }

    private fun buildCategoryPayload(totals: DoubleArray): Pair<IntArray, DoubleArray> {
        var count = 0
        for (value in totals) {
            if (value > 0.0) count++
        }
        if (count == 0) {
            return Pair(IntArray(0), DoubleArray(0))
        }
        val ids = IntArray(count)
        val paid = DoubleArray(count)
        var i = 0
        for (category in Category.values()) {
            val value = totals[category.id]
            if (value <= 0.0) continue
            ids[i] = category.id
            paid[i] = value
            i++
        }
        return Pair(ids, paid)
    }
}
