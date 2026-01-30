package ca.l1cked.dsellsystem.managers

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.util.MessageUtil
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import java.util.UUID
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent

class WorthLoreManager(private val plugin: DSellSystemPlugin) : Listener {
    private val openShulkerSlots: MutableMap<UUID, Int> = HashMap()
    private val updateCooldowns: MutableMap<UUID, Long> = HashMap()
    private val lastActionBarSent: MutableMap<UUID, Long> = HashMap()

    fun isEnabled(): Boolean =
        plugin.config.getBoolean("settings.worth-lore.enabled", false)

    fun getFormat(): String =
        plugin.config.getString("settings.worth-lore.format", "&fWorth: &a$%price%") ?: ""

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!isEnabled()) return
        val player = event.player
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            updateInventory(player)
        }, 20L)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (isOnCooldown(player.uniqueId)) return
        val title = MessageUtil.serialize(event.view.title())
        if (shouldSkipInventory(title)) return

        val clicked = event.currentItem
        if (clicked != null && clicked.type != Material.AIR) {
            showWorthActionBar(player, totalStackWorth(clicked, player))
        }

        if (event.inventory.holder is ShulkerBox) {
            if (!openShulkerSlots.containsKey(player.uniqueId)) {
                val inv = player.inventory
                for (i in 0 until inv.size) {
                    val item = inv.getItem(i)
                    if (item != null && isShulkerBox(item.type)) {
                        openShulkerSlots[player.uniqueId] = i
                        break
                    }
                }
            }
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                updateOpenShulker(player)
            }, 2L)
        }

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (isEnabled()) {
                updateInventory(player)
            } else {
                clearWorthLore(player.inventory)
            }
            updateCooldowns[player.uniqueId] = System.currentTimeMillis()
        }, 2L)
    }


    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (isOnCooldown(player.uniqueId)) return
        val title = MessageUtil.serialize(event.view.title())
        if (shouldSkipInventory(title)) return
        val top = event.view.topInventory
        val rawSlots = event.rawSlots
        for (slot in rawSlots) {
            val item = if (slot < top.size) top.getItem(slot) else player.inventory.getItem(slot - top.size)
            if (item != null && item.type != Material.AIR) {
                showWorthActionBar(player, totalStackWorth(item, player))
                break
            }
        }
    }


    @EventHandler
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        if (!isEnabled()) return
        val item = player.inventory.getItem(event.newSlot) ?: return
        if (item.type == Material.AIR) return
        showWorthActionBar(player, totalStackWorth(item, player))
    }

    @EventHandler
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!isEnabled()) return
        val item = event.mainHandItem
        if (item.type == Material.AIR) return
        showWorthActionBar(player, totalStackWorth(item, player))
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (event.inventory.holder is ShulkerBox) {
            val slot = openShulkerSlots.remove(player.uniqueId) ?: return
            if (!isEnabled()) return
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                updateShulkerSlot(player, slot)
            }, 1L)
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!isEnabled()) return
        val type = event.block.type
        if (isShulkerBox(type)) {
            val player = event.player
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                updateInventory(player)
            }, 3L)
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!isEnabled()) return
        val item = event.itemInHand
        if (item.type != Material.AIR && isShulkerBox(item.type)) {
            val player = event.player
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                updateInventory(player)
            }, 1L)
        }
    }

    @EventHandler
    fun onItemPickup(event: EntityPickupItemEvent) {
        if (!isEnabled()) return
        val player = event.entity as? Player ?: return
        val item = event.item.itemStack
        if (isShulkerBox(item.type) || plugin.pricesManager.getPrice(item.type) > 0) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                updateInventory(player)
            }, 2L)
        }
    }


    private fun showWorthActionBar(player: Player, totalWorth: Double) {
        val now = System.currentTimeMillis()
        val last = lastActionBarSent[player.uniqueId] ?: 0L
        if (now - last < ACTION_BAR_COOLDOWN_MS) return
        lastActionBarSent[player.uniqueId] = now
        val msg = getFormat().replace("%price%", MessageUtil.formatMoney(totalWorth))
        player.sendActionBar(MessageUtil.deserialize(msg))
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            // clear only if no newer message was sent
            val current = lastActionBarSent[player.uniqueId] ?: return@Runnable
            if (current != now) return@Runnable
            player.sendActionBar(MessageUtil.deserialize(""))
        }, ACTION_BAR_CLEAR_MS)
    }

    private fun totalStackWorth(item: ItemStack, player: Player): Double {
        val perItem = calculateWorthPerItem(item, player)
        return perItem * item.amount.toDouble()
    }

    fun updateInventory(player: Player) {
        val inventory = player.inventory
        if (!isEnabled()) {
            clearWorthLore(inventory)
            return
        }
        var size = inventory.size
        if (size > 54) size = 54
        for (slot in 0 until size) {
            val item = inventory.getItem(slot) ?: continue
            if (item.type == Material.AIR) continue
            val price = plugin.pricesManager.getPrice(item.type)
            if (price <= 0 && !isShulkerBox(item.type)) continue
            val updated = updateItemLore(item, player) ?: continue
            inventory.setItem(slot, updated)
        }
    }

    fun updateItemLore(item: ItemStack?, player: Player): ItemStack? {
        if (item == null || item.type == Material.AIR) return null
        val totalPrice = calculateWorthPerItem(item, player)
        val clone = item.clone()
        val meta = clone.itemMeta ?: return null
        val lore = meta.lore()?.toMutableList() ?: mutableListOf()
        lore.removeIf { shouldRemoveLine(it) }
        if (totalPrice > 0) {
            val format = getFormat()
            val line = format.replace("%price%", MessageUtil.formatMoney(totalPrice))
            lore.add(MessageUtil.deserialize(WORTH_LORE_MARKER + line))
        }
        if (lore.isEmpty()) {
            meta.lore(null)
        } else {
            meta.lore(lore)
        }
        clone.itemMeta = meta
        return clone
    }

    private fun calculateWorthPerItem(item: ItemStack, player: Player): Double {
        val basePrice = plugin.pricesManager.getPrice(item.type)
        val category = plugin.pricesManager.getCategory(item.type)
        val multiplier = if (category != null) {
            plugin.playerDataManager.getPlayerData(player.uniqueId).getMultiplier(category)
        } else {
            1.0
        }
        var total = 0.0
        if (basePrice > 0.0) {
            total += basePrice * multiplier
        }
        if (isShulkerBox(item.type)) {
            total += calculateShulkerContents(item, player)
        }
        return total
    }

    private fun isShulkerBox(material: Material): Boolean =
        material.name.contains("SHULKER_BOX")

    private fun calculateShulkerContents(item: ItemStack, player: Player): Double {
        val meta = item.itemMeta
        if (meta !is BlockStateMeta) return 0.0
        val state = meta.blockState
        if (state !is ShulkerBox) return 0.0
        val data = plugin.playerDataManager.getPlayerData(player.uniqueId)
        var total = 0.0
        for (stack in state.inventory.contents) {
            if (stack == null || stack.type == Material.AIR) continue
            val price = plugin.pricesManager.getPrice(stack.type)
            if (price <= 0.0) continue
            val category = plugin.pricesManager.getCategory(stack.type)
            val multiplier = if (category != null) data.getMultiplier(category) else 1.0
            total += price * stack.amount * multiplier
        }
        return total
    }

    private fun clearWorthLore(inventory: Inventory) {
        for (slot in 0 until inventory.size) {
            val item = inventory.getItem(slot) ?: continue
            if (item.type == Material.AIR) continue
            val updated = clearItemLore(item) ?: continue
            inventory.setItem(slot, updated)
        }
    }

    private fun clearItemLore(item: ItemStack?): ItemStack? {
        if (item == null || item.type == Material.AIR) return null
        val meta = item.itemMeta ?: return null
        if (!meta.hasLore()) return null
        val lore = meta.lore()?.toMutableList() ?: mutableListOf()
        val removed = lore.removeIf { shouldRemoveLine(it) }
        if (!removed) return null
        val clone = item.clone()
        val cloneMeta = clone.itemMeta ?: return null
        if (lore.isEmpty()) {
            cloneMeta.lore(null)
        } else {
            cloneMeta.lore(lore)
        }
        clone.itemMeta = cloneMeta
        return clone
    }

    fun clearAllPlayerLore(player: Player) {
        clearWorthLore(player.inventory)
    }

    private fun isOnCooldown(uuid: UUID): Boolean {
        val last = updateCooldowns[uuid] ?: return false
        return System.currentTimeMillis() - last < COOLDOWN_MS
    }

    private fun shouldSkipInventory(title: String): Boolean {
        val skip = arrayOf(
            MessageUtil.legacy(plugin.guiManager.getSellGUITitle()),
            MessageUtil.legacy(plugin.guiManager.getWorthGUITitle().split("%")[0].trim()),
            MessageUtil.legacy("ITEM PRICES"),
        )
        for (value in skip) {
            if (value.isNotEmpty() && title.contains(value)) {
                return true
            }
        }
        return false
    }

    fun cleanupCooldowns() {
        val now = System.currentTimeMillis()
        updateCooldowns.entries.removeIf { now - it.value > 60000L }
    }

    private fun updateOpenShulker(player: Player) {
        val slot = openShulkerSlots[player.uniqueId] ?: return
        updateShulkerSlot(player, slot)
    }

    private fun updateShulkerSlot(player: Player, slot: Int) {
        val item = player.inventory.getItem(slot) ?: return
        if (!isShulkerBox(item.type)) return
        val updated = updateItemLore(item, player) ?: return
        player.inventory.setItem(slot, updated)
    }

    private fun shouldRemoveLine(line: Component): Boolean {
        val raw = MessageUtil.serialize(line)
        return raw.contains(WORTH_LORE_MARKER) ||
            raw.contains("Worth:") ||
            raw.contains("&fWorth:") ||
            raw.contains("\u00A7fWorth:")
    }

    private companion object {
        const val COOLDOWN_MS = 100L
        private const val ACTION_BAR_COOLDOWN_MS = 150L
        private const val ACTION_BAR_CLEAR_MS = 40L
        private const val WORTH_LORE_MARKER = "\u00A7x\u00A7w\u00A7o\u00A7r\u00A7t\u00A7h"
    }
}
