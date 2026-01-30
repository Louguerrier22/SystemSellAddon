package ca.l1cked.dsellsystem.gui

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.util.MessageUtil
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import java.util.UUID

class WorthResetGUI(private val plugin: DSellSystemPlugin) : Listener {
    private val states: MutableMap<UUID, View> = HashMap()

    private enum class View { ROOT, PLAYER_SELECT }

    fun openRoot(player: Player) {
        states[player.uniqueId] = View.ROOT
        val inv = Bukkit.createInventory(null, 27, MessageUtil.deserialize(ROOT_TITLE))
        fillRoot(inv)
        player.openInventory(inv)
    }

    private fun openPlayerSelect(player: Player) {
        states[player.uniqueId] = View.PLAYER_SELECT
        val inv = Bukkit.createInventory(null, 54, MessageUtil.deserialize(SELECT_TITLE))
        fillPlayerSelect(inv)
        player.openInventory(inv)
    }

    private fun fillRoot(inv: Inventory) {
        inv.clear()
        inv.setItem(11, actionItem("Reset Your Worth Lore", Material.SPONGE, "&7Click to clear your worth lore"))
        inv.setItem(13, actionItem("Select Player", Material.PLAYER_HEAD, "&7Pick a player to clear"))
        inv.setItem(15, actionItem("Reset All Online", Material.REDSTONE_BLOCK, "&7Clear worth lore for everyone online"))
        inv.setItem(18, actionItem("Back", Material.ARROW, "&7Return to settings"))
    }

    private fun fillPlayerSelect(inv: Inventory) {
        inv.clear()
        var slot = 0
        for (player in Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break
            inv.setItem(slot++, playerHead(player))
        }
        inv.setItem(49, actionItem("Back", Material.ARROW, "&7Return to reset menu"))
    }

    private fun playerHead(player: Player): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = player
        meta.displayName(MessageUtil.deserialize("&f" + player.name))
        meta.lore(MessageUtil.colorizeList(listOf("&7Click to clear worth lore")))
        meta.persistentDataContainer.set(playerKey(), PersistentDataType.STRING, player.uniqueId.toString())
        item.itemMeta = meta
        return item
    }

    private fun actionItem(name: String, mat: Material, lore: String): ItemStack {
        val item = ItemStack(mat)
        val meta = item.itemMeta ?: return item
        meta.displayName(MessageUtil.deserialize("&f$name"))
        meta.lore(MessageUtil.colorizeList(listOf(lore)))
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onCreative(event: InventoryCreativeEvent) {
        val title = MessageUtil.serialize(event.view.title())
        if (title != MessageUtil.legacy(ROOT_TITLE) && title != MessageUtil.legacy(SELECT_TITLE)) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        plugin.server.scheduler.runTask(plugin, Runnable {
            player.updateInventory()
        })
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        val title = MessageUtil.serialize(event.view.title())
        if (title != MessageUtil.legacy(ROOT_TITLE) && title != MessageUtil.legacy(SELECT_TITLE)) return
        event.isCancelled = true
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = MessageUtil.serialize(event.view.title())
        if (title != MessageUtil.legacy(ROOT_TITLE) && title != MessageUtil.legacy(SELECT_TITLE)) return
        event.isCancelled = true

        when (states[player.uniqueId]) {
            View.ROOT -> handleRootClick(player, event.rawSlot)
            View.PLAYER_SELECT -> handlePlayerSelectClick(player, event.rawSlot, event.currentItem)
            else -> {}
        }
    }

    private fun handleRootClick(player: Player, rawSlot: Int) {
        when (rawSlot) {
            11 -> {
                plugin.worthLoreManager.clearAllPlayerLore(player)
                refreshPlayer(player)
                MessageUtil.sendMessage(player, "&aCleared your worth lore.")
            }
            13 -> openPlayerSelect(player)
            15 -> {
                for (p in Bukkit.getOnlinePlayers()) {
                    plugin.worthLoreManager.clearAllPlayerLore(p)
                    refreshPlayer(p)
                }
                MessageUtil.sendMessage(player, "&aCleared worth lore for all online players.")
            }
            18 -> plugin.settingsGUI.open(player)
        }
    }

    private fun handlePlayerSelectClick(player: Player, rawSlot: Int, clicked: ItemStack?) {
        if (rawSlot == 49) {
            openRoot(player)
            return
        }
        val meta = clicked?.itemMeta as? SkullMeta ?: return
        val target = resolveTarget(meta) ?: return
        plugin.worthLoreManager.clearAllPlayerLore(target)
        refreshPlayer(target)
        MessageUtil.sendMessage(player, "&aCleared worth lore for &f${target.name}&a.")
    }

    private fun refreshPlayer(player: Player) {
        if (player.gameMode == GameMode.CREATIVE) return
        if (plugin.worthOverlay.isAvailable()) {
            plugin.worthOverlay.refreshPlayer(player)
        }
    }

    private fun resolveTarget(meta: SkullMeta): Player? {
        val stored = meta.persistentDataContainer.get(playerKey(), PersistentDataType.STRING)
        if (!stored.isNullOrEmpty()) {
            val uuid = runCatching { UUID.fromString(stored) }.getOrNull()
            if (uuid != null) {
                return Bukkit.getPlayer(uuid)
            }
        }
        return meta.owningPlayer?.player
    }

    private fun playerKey(): NamespacedKey = NamespacedKey(plugin, "worth_reset_player")

    private companion object {
        private const val ROOT_TITLE = "&8Reset Worth Lore"
        private const val SELECT_TITLE = "&8Select Player"
    }
}
