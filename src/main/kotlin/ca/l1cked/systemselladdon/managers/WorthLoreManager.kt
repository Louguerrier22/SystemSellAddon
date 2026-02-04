package ca.l1cked.systemselladdon.managers

import ca.l1cked.systemselladdon.SystemSellAddonPlugin
import ca.l1cked.systemselladdon.util.MessageUtil
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
import java.util.UUID
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.GameMode
import org.bukkit.event.player.PlayerGameModeChangeEvent

class WorthLoreManager(private val plugin: SystemSellAddonPlugin) : Listener {
    private val openShulkerSlots: MutableMap<UUID, Int> = HashMap()
    private val updateCooldowns: MutableMap<UUID, Long> = HashMap()

    fun isEnabled(): Boolean =
        isWorthLoreConfigured() && isProtocolLibAvailable()

    private fun isWorthLoreConfigured(): Boolean =
        plugin.config.getBoolean("settings.worth-lore.enabled", false)

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.playerDataManager.getPlayerData(player.uniqueId)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            updateInventory(player)
        }, 20L)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (player.gameMode == GameMode.CREATIVE || event.view.type == InventoryType.CREATIVE) {
            clearWorthLore(player.inventory)
            clearCursorWorth(player)
            return
        }
        if (isOnCooldown(player.uniqueId)) return
        val title = MessageUtil.serialize(event.view.title())
        if (shouldSkipInventory(title)) return

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
            } else if (!isWorthLoreConfigured()) {
                clearWorthLore(player.inventory)
            }
            updateCooldowns[player.uniqueId] = System.currentTimeMillis()
        }, 2L)
    }


    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (player.gameMode == GameMode.CREATIVE || event.view.type == InventoryType.CREATIVE) {
            clearWorthLore(player.inventory)
            clearCursorWorth(player)
            return
        }
        if (isOnCooldown(player.uniqueId)) return
        val title = MessageUtil.serialize(event.view.title())
        if (shouldSkipInventory(title)) return

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
            } else if (!isWorthLoreConfigured()) {
                clearWorthLore(player.inventory)
            }
            updateCooldowns[player.uniqueId] = System.currentTimeMillis()
        }, 2L)
    }


    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (player.gameMode == GameMode.CREATIVE) {
            clearWorthLore(player.inventory)
            return
        }
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
        if (event.player.gameMode == GameMode.CREATIVE) return
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
        if (event.player.gameMode == GameMode.CREATIVE) return
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
        if (player.gameMode == GameMode.CREATIVE) {
            clearWorthLore(player.inventory)
            return
        }
        val item = event.item.itemStack
        if (isShulkerBox(item.type) || plugin.pricesManager.getPrice(item.type) > 0) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                updateInventory(player)
            }, 2L)
        }
    }

    @EventHandler
    fun onItemDrop(event: PlayerDropItemEvent) {
        if (!isEnabled()) return
        val player = event.player
        if (player.gameMode == GameMode.CREATIVE) {
            clearWorthLore(player.inventory)
            clearCursorWorth(player)
            return
        }
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            updateInventory(player)
        }, 1L)
    }

    @EventHandler
    fun onInventoryMove(event: InventoryMoveItemEvent) {
        if (!isEnabled()) return
        val sourceHolder = event.source.holder
        val destHolder = event.destination.holder
        val players = mutableSetOf<Player>()
        if (sourceHolder is Player) players.add(sourceHolder)
        if (destHolder is Player) players.add(destHolder)
        if (players.isEmpty()) return
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            for (player in players) {
                if (player.gameMode == GameMode.CREATIVE) {
                    clearWorthLore(player.inventory)
                    clearCursorWorth(player)
                    continue
                }
                updateInventory(player)
            }
        }, 1L)
    }

    @EventHandler
    fun onCreativeInventory(event: InventoryCreativeEvent) {
        val player = event.whoClicked as? Player ?: return
        if (player.gameMode != GameMode.CREATIVE) return
        clearWorthLore(player.inventory)
        clearCursorWorth(player)
    }

    @EventHandler
    fun onGameModeChange(event: PlayerGameModeChangeEvent) {
        if (event.newGameMode == GameMode.CREATIVE) {
            val player = event.player
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                clearWorthLore(player.inventory)
                clearCursorWorth(player)
                player.updateInventory()
            }, 1L)
        }
    }


    fun updateInventory(player: Player) {
        if (!isWorthLoreConfigured()) {
            clearWorthLore(player.inventory)
            return
        }
        if (player.gameMode == GameMode.CREATIVE) return
        if (!isProtocolLibAvailable()) return
        player.updateInventory()
    }

    private fun isShulkerBox(material: Material): Boolean =
        material.name.contains("SHULKER_BOX")

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

    private fun clearCursorWorth(player: Player) {
        val cursor = player.itemOnCursor
        if (cursor.type == Material.AIR) return
        val updated = clearItemLore(cursor) ?: return
        player.setItemOnCursor(updated)
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
        updateInventory(player)
    }

    private fun shouldRemoveLine(line: Component): Boolean {
        val raw = MessageUtil.serialize(line)
        if (raw.contains(WORTH_LORE_MARKER) || raw.contains(LEGACY_WORTH_LORE_MARKER)) {
            return true
        }
        val prefixLegacy = formatPrefixLegacy()
        return prefixLegacy.isNotEmpty() && raw.contains(prefixLegacy)
    }

    private fun formatPrefixLegacy(): String {
        val format = plugin.config.getString("settings.worth-lore.format", "&fWorth: &a$%price%") ?: ""
        val prefix = format.substringBefore("%price%")
        if (prefix.isEmpty()) return ""
        return MessageUtil.legacy(prefix)
    }

    private fun isProtocolLibAvailable(): Boolean =
        plugin.server.pluginManager.getPlugin("ProtocolLib")?.isEnabled == true

    private companion object {
        const val COOLDOWN_MS = 100L
        private const val WORTH_LORE_MARKER = "\u00A7x\u00A7w\u00A7o\u00A7r\u00A7t\u00A7h"
        private const val LEGACY_WORTH_LORE_MARKER =
            "\u00C2\u00A7x\u00C2\u00A7w\u00C2\u00A7o\u00C2\u00A7r\u00C2\u00A7t\u00C2\u00A7h"
    }
}



