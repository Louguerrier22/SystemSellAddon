package ca.l1cked.systemselladdon.sell

import ca.l1cked.systemselladdon.api.event.SystemSellAddonPayoutEvent
import ca.l1cked.systemselladdon.api.event.SystemSellAddonPayoutSummaryEvent
import ca.l1cked.systemselladdon.category.Category
import ca.l1cked.systemselladdon.SystemSellAddonPlugin
import ca.l1cked.systemselladdon.storage.SellStorage
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
        if (!SystemSellAddonPlugin.instance.config.getBoolean("settings.enabled", true)) {
            return SellOutcome.EconomyFailure("disabled")
        }
        val inv = player.inventory

        val baseTotals = linkedMapOf<String, Double>()
        val displays = linkedMapOf<String, String>()
        val slotsToClear = mutableListOf<Int>()
        val soldItems = mutableListOf<ItemStack>()
        val hasDetailListeners = SystemSellAddonPayoutEvent.hasListeners()
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
        val historyEnabled = SystemSellAddonPlugin.instance.config.getBoolean("history.enabled", false)
        val paidByCategorySnapshot = paidByCategory.toMap()
        val soldItemsSnapshot = soldItems.map { it.clone() }

        val fireEvents: (Long?) -> Unit = { txId ->
            if (SystemSellAddonPayoutSummaryEvent.hasListeners()) {
                Bukkit.getPluginManager().callEvent(
                    SystemSellAddonPayoutSummaryEvent(
                        player = player,
                        personalMultiplier = multiplier,
                        totalPaid = total,
                        transactionId = txId,
                        timestampMillis = now,
                    )
                )
            }

            if (categoryTotals != null) {
                for ((categoryKey, paid) in paidByCategorySnapshot) {
                    val id = Category.idForKey(categoryKey) ?: continue
                    categoryTotals[id] += paid
                }
                val (ids, paid) = buildCategoryPayload(categoryTotals)
                Bukkit.getPluginManager().callEvent(
                    SystemSellAddonPayoutEvent(
                        player = player,
                        categoryIds = ids,
                        paidByCategory = paid,
                        personalMultiplier = multiplier,
                        totalPaid = total,
                        transactionId = txId,
                        timestampMillis = now,
                    )
                )
            }
            val bonusPayload = DoubleArray(Category.size)
            for ((categoryKey, paid) in paidByCategorySnapshot) {
                val id = Category.idForKey(categoryKey) ?: continue
                if (id in 0 until Category.size) {
                    bonusPayload[id] = paid
                }
            }
            SystemSellAddonPlugin.instance.mayorBonus.applyMayorBonus(
                player = player,
                paidByCategory = bonusPayload,
                totalPaid = total,
                transactionId = txId?.toString()
            )
        }

        if (historyEnabled) {
            SystemSellAddonPlugin.instance.ioScope.launch {
                val txId = try {
                    storage.recordSell(
                        playerUuid = player.uniqueId,
                        playerName = player.name,
                        timestampMillis = now,
                        personalMultiplier = multiplier,
                        paidByCategory = paidByCategorySnapshot,
                        totalPaid = total,
                    )
                } catch (t: Throwable) {
                    SystemSellAddonPlugin.instance.logger.severe("Failed to record sell history for ${player.name}")
                    t.printStackTrace()
                    -1L
                }

                if (txId > 0 && soldItemsSnapshot.isNotEmpty()) {
                    try {
                        storage.recordSellItems(txId, soldItemsSnapshot)
                    } catch (t: Throwable) {
                        SystemSellAddonPlugin.instance.logger.severe("Failed to record sell item history for ${player.name}")
                        t.printStackTrace()
                    }
                }

                try {
                    SystemSellAddonPlugin.instance.historyService.refreshKnownNames()
                } catch (_: Throwable) {
                    // ignore cache refresh failures
                }

                SystemSellAddonPlugin.instance.server.scheduler.runTask(SystemSellAddonPlugin.instance, Runnable {
                    fireEvents(if (txId > 0) txId else null)
                })
            }
        } else {
            fireEvents(null)
        }

        return SellOutcome.Success(
            SellResult(
                transactionId = -1L,
                personalMultiplier = multiplier,
                paidByCategory = paidByCategorySnapshot,
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



