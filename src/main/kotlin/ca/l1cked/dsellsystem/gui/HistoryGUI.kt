package ca.l1cked.dsellsystem.gui

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.storage.SellItemEntry
import ca.l1cked.dsellsystem.util.MessageUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.math.ceil
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.block.ShulkerBox
import kotlinx.coroutines.launch

class HistoryGUI(private val plugin: DSellSystemPlugin) : Listener {
    private data class State(
        var page: Int,
        var target: UUID,
        var targetName: String,
        var maxPage: Int = 1,
        var requestId: Long = 0,
    )
    private class HistoryHolder(var state: State) : InventoryHolder {
        override fun getInventory(): Inventory = Bukkit.createInventory(this, 9)
    }
    private val states: MutableMap<UUID, State> = HashMap()

    fun open(player: Player, target: Player) = open(player, target.uniqueId, target.name)

    fun open(player: Player, targetId: UUID, targetName: String) {
        val state = states.computeIfAbsent(player.uniqueId) { State(1, targetId, targetName) }
        state.target = targetId
        state.targetName = targetName
        render(player, state)
    }

    private fun render(player: Player, state: State) {
        val pageSize = 45
        val targetId = state.target
        val targetName = state.targetName
        val page = state.page
        val requestId = ++state.requestId
        val offset = (page - 1) * pageSize

        val loadingTitle = MessageUtil.deserialize("&8Sell History: &f$targetName &7(Loading...)")
        val loadingHolder = HistoryHolder(state)
        val loadingInv = Bukkit.createInventory(loadingHolder, 54, loadingTitle)
        loadingInv.setItem(22, loadingItem())
        player.openInventory(loadingInv)

        plugin.ioScope.launch {
            val entries = plugin.historyService.itemHistoryFor(targetId, pageSize, offset)
            val total = plugin.historyService.itemHistoryCount(targetId)
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                if (!states.containsKey(player.uniqueId)) return@Runnable
                if (state.requestId != requestId) return@Runnable

                val maxPage = maxOf(1, ceil(total / pageSize.toDouble()).toInt())
                if (state.page > maxPage) state.page = maxPage
                state.maxPage = maxPage

                val title = MessageUtil.deserialize("&8Sell History: &f$targetName &7(${state.page}/$maxPage)")
                val holder = HistoryHolder(state)
                val inv = Bukkit.createInventory(holder, 54, title)

                for (i in entries.indices) {
                    val entry = entries[i]
                    val item = entry.item.clone()
                    item.amount = entry.item.amount
                    val meta = item.itemMeta
                    if (meta != null) {
                        val time = plugin.historyService.formatTimestamp(entry.timestampMillis)
                        val basePrice = plugin.pricesManager.getPrice(item.type)
                        val category = plugin.pricesManager.getCategory(item.type)
                        val multiplier = if (category != null) {
                            plugin.playerDataManager.getPlayerData(targetId).getMultiplier(category)
                        } else {
                            1.0
                        }
                        val baseWorth = basePrice * item.amount.toDouble() * multiplier
                        val shulkerWorth = if (isShulkerBox(item.type)) shulkerContentsWorth(item, targetId) else 0.0
                        val worth = baseWorth + shulkerWorth
                        val extra = mutableListOf<String>()
                        extra.add("&7Sold at: &f$time")
                        extra.add("&7Amount: &f${entry.item.amount}")
                        if (basePrice > 0.0) {
                            extra.add("&7Worth: &a${plugin.economy.format(worth)}")
                        }

                        val existing = meta.lore()?.filterNot { shouldRemoveWorthLine(MessageUtil.serialize(it)) } ?: emptyList()
                        val lore = existing + MessageUtil.colorizeList(extra)
                        meta.lore(lore)
                        item.itemMeta = meta
                    }
                    inv.setItem(i, item)
                }

                if (state.page > 1) {
                    inv.setItem(45, navItem("&aPrev", Material.ARROW))
                }
                if (state.page < maxPage) {
                    inv.setItem(53, navItem("&aNext", Material.ARROW))
                }

                player.openInventory(inv)
            })
        }
    }


    private fun shouldRemoveWorthLine(raw: String): Boolean {
        return raw.contains("Worth:") || raw.contains("\u00A7x\u00A7w\u00A7o\u00A7r\u00A7t\u00A7h")
    }


    private fun isShulkerBox(material: Material): Boolean =
        material.name.contains("SHULKER_BOX")

    private fun shulkerContentsWorth(item: ItemStack, playerId: UUID): Double {
        val meta = item.itemMeta as? BlockStateMeta ?: return 0.0
        val state = meta.blockState as? ShulkerBox ?: return 0.0
        val data = plugin.playerDataManager.getPlayerData(playerId)
        var total = 0.0
        for (stack in state.inventory.contents) {
            if (stack == null || stack.type == Material.AIR || stack.amount <= 0) continue
            val base = plugin.pricesManager.getPrice(stack.type)
            if (base <= 0.0) continue
            val category = plugin.pricesManager.getCategory(stack.type)
            val multiplier = if (category != null) data.getMultiplier(category) else 1.0
            total += base * stack.amount.toDouble() * multiplier
        }
        return total
    }

    private fun navItem(name: String, mat: Material): ItemStack {
        val item = ItemStack(mat)
        val meta = item.itemMeta ?: return item
        meta.displayName(MessageUtil.deserialize(name))
        item.itemMeta = meta
        return item
    }

    private fun loadingItem(): ItemStack {
        val item = ItemStack(Material.CLOCK)
        val meta = item.itemMeta ?: return item
        meta.displayName(MessageUtil.deserialize("&7Loading..."))
        item.itemMeta = meta
        return item
    }



    @EventHandler
    fun onCreative(event: InventoryCreativeEvent) {
        val holder = event.view.topInventory.holder as? HistoryHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        plugin.server.scheduler.runTask(plugin, Runnable {
            player.updateInventory()
        })
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? HistoryHolder ?: return
        event.isCancelled = true
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? HistoryHolder ?: return
        val state = holder.state
        event.isCancelled = true

        when (event.rawSlot) {
            45 -> {
                state.page = (state.page - 1).coerceAtLeast(1)
                render(player, state)
            }
            53 -> {
                if (state.page < state.maxPage) {
                    state.page += 1
                    render(player, state)
                }
            }
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val holder = event.view.topInventory.holder as? HistoryHolder ?: return
        states.remove(player.uniqueId)
    }
}
