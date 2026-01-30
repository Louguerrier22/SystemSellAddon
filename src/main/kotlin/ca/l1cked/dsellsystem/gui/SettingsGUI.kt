package ca.l1cked.dsellsystem.gui

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.util.MessageUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryCreativeEvent

class SettingsGUI(private val plugin: DSellSystemPlugin) : Listener {

    fun open(player: Player) {
        val title = MessageUtil.deserialize("&8DSellSystem Settings")
        val inv = Bukkit.createInventory(null, 9, title)
        fill(inv, player)
        player.openInventory(inv)
    }

    private fun fill(inv: Inventory, viewer: Player?) {
        inv.clear()
        setBorders(inv)
        inv.setItem(2, toggleItem("Plugin", plugin.config.getBoolean("settings.enabled", true)))
        inv.setItem(3, toggleItem("History", plugin.config.getBoolean("history.enabled", false)))
        inv.setItem(4, actionItem("Reload", Material.NETHER_STAR, "&aReload config"))
        if (hasResetPermission(viewer)) {
            inv.setItem(5, actionItem("Reset Worth Lore", Material.SPONGE, "&7Open reset menu"))
        }
        inv.setItem(6, protocolStatusItem())
    }

    private fun toggleItem(name: String, enabled: Boolean): ItemStack {
        val mat = if (enabled) Material.LIME_DYE else Material.RED_DYE
        val item = ItemStack(mat)
        val meta = item.itemMeta ?: return item
        meta.displayName(MessageUtil.deserialize("&f$name: " + if (enabled) "&aON" else "&cOFF"))
        meta.lore(MessageUtil.colorizeList(listOf("&7Click to toggle")))
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

    private fun protocolStatusItem(): ItemStack {
        val pluginManager = plugin.server.pluginManager
        val protocol = pluginManager.getPlugin("ProtocolLib")
        val available = protocol?.isEnabled == true
        val mat = if (available) Material.LIME_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE
        val item = ItemStack(mat)
        val meta = item.itemMeta ?: return item
        meta.displayName(MessageUtil.deserialize("&fProtocolLib"))
        val status = if (available) "&aDetected" else "&cNot installed"
        val version = protocol?.pluginMeta?.version ?: "-"
        val worthEnabled = plugin.config.getBoolean("settings.worth-lore.enabled", false)
        val overlayActive = available && worthEnabled
        val overlayStatus = if (overlayActive) "&aActive" else "&cInactive"
        meta.lore(
            MessageUtil.colorizeList(
                listOf(
                    "&7Status: $status",
                    "&7Version: &f$version",
                    "&7Worth overlay: $overlayStatus",
                )
            )
        )
        item.itemMeta = meta
        return item
    }




    @EventHandler
    fun onCreative(event: InventoryCreativeEvent) {
        val title = MessageUtil.serialize(event.view.title())
        if (title != MessageUtil.legacy("&8DSellSystem Settings")) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        plugin.server.scheduler.runTask(plugin, Runnable {
            player.updateInventory()
        })
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        val title = MessageUtil.serialize(event.view.title())
        if (title != MessageUtil.legacy("&8DSellSystem Settings")) return
        event.isCancelled = true
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = MessageUtil.serialize(event.view.title())
        if (title != MessageUtil.legacy("&8DSellSystem Settings")) return
        event.isCancelled = true

        when (event.rawSlot) {
            2 -> {
                val key = "settings.enabled"
                val next = !plugin.config.getBoolean(key, true)
                plugin.config.set(key, next)
                plugin.saveConfig()
                fill(event.inventory, player)
                MessageUtil.sendMessage(player, if (next) "&aPlugin enabled." else "&cPlugin disabled.")
            }
            3 -> {
                val key = "history.enabled"
                val next = !plugin.config.getBoolean(key, false)
                plugin.config.set(key, next)
                plugin.saveConfig()
                fill(event.inventory, player)
                MessageUtil.sendMessage(player, if (next) "&aHistory saving enabled." else "&cHistory saving disabled.")
            }
            4 -> {
                plugin.reloadConfig()
                plugin.guiManager.reload()
                plugin.pricesManager.reload()
                for (p in Bukkit.getOnlinePlayers()) {
                    plugin.worthOverlay.refreshPlayer(p)
                }
                fill(event.inventory, player)
                MessageUtil.sendMessage(player, "&aDSellSystem configuration reloaded!")
            }
            5 -> {
                if (!player.hasPermission("dsellsystem.resetworth")) {
                    MessageUtil.sendMessage(player, "&cYou don't have permission to do that.")
                    return
                }
                plugin.worthResetGUI.openRoot(player)
            }
        }
    }

    private fun setBorders(inv: Inventory) {
        val pane = borderPane()
        inv.setItem(0, pane)
        inv.setItem(1, pane)
        inv.setItem(7, pane)
        inv.setItem(8, pane)
    }

    private fun borderPane(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.displayName(MessageUtil.deserialize(" "))
        item.itemMeta = meta
        return item
    }

    private fun hasResetPermission(player: Player?): Boolean {
        return player?.hasPermission("dsellsystem.resetworth") ?: true
    }
}
