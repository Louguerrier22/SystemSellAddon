package ca.l1cked.systemselladdon.managers

import ca.l1cked.systemselladdon.SystemSellAddonPlugin
import ca.l1cked.systemselladdon.category.Category
import ca.l1cked.systemselladdon.util.MessageUtil
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ProtocolWorthOverlay(private val plugin: SystemSellAddonPlugin) : WorthOverlay {
    private val protocolManager = ProtocolLibrary.getProtocolManager()
    private var listener: PacketAdapter? = null
    private val snapshots: MutableMap<UUID, Snapshot> = ConcurrentHashMap()

    private data class Snapshot(
        val allowOverlay: Boolean,
        val skipOverlay: Boolean,
        val multipliers: DoubleArray,
    )

    override fun isAvailable(): Boolean =
        plugin.server.pluginManager.getPlugin("ProtocolLib")?.isEnabled == true

    private fun isEnabled(): Boolean =
        plugin.config.getBoolean("settings.worth-lore.enabled", false) && isAvailable()

    override fun enable() {
        if (!isEnabled() || listener != null) return
        listener = object : PacketAdapter(
            plugin,
            ListenerPriority.NORMAL,
            PacketType.Play.Server.SET_SLOT,
            PacketType.Play.Server.WINDOW_ITEMS,
        ) {
            override fun onPacketSending(event: PacketEvent) {
                if (!isEnabled()) return
                when (event.packetType) {
                    PacketType.Play.Server.SET_SLOT -> {
                        val item = event.packet.itemModifier.read(0)
                        val updated = applyWorth(event.player, item)
                        event.packet.itemModifier.write(0, updated)
                    }
                    PacketType.Play.Server.WINDOW_ITEMS -> {
                        val items = event.packet.itemListModifier.read(0)
                        val updated = items.map { applyWorth(event.player, it) }
                        event.packet.itemListModifier.write(0, updated)
                    }
                }
            }
        }
        protocolManager.addPacketListener(listener)
        plugin.server.onlinePlayers.forEach { updateSnapshot(it) }
    }

    override fun disable() {
        listener?.let { protocolManager.removePacketListener(it) }
        listener = null
        snapshots.clear()
    }

    override fun refreshPlayer(player: Player) {
        runOnMain {
            updateSnapshot(player)
            player.updateInventory()
        }
    }

    private fun applyWorth(player: Player, item: ItemStack?): ItemStack? {
        val snapshot = snapshots[player.uniqueId] ?: return item
        if (!snapshot.allowOverlay || snapshot.skipOverlay) return item
        if (item == null || item.type == Material.AIR) return item
        val format = plugin.config.getString("settings.worth-lore.format", "&fWorth: &a$%price%")
            ?: ""
        if (format.isEmpty()) return item
        val prefixLegacy = formatPrefixLegacy(format)

        val totalWorth = totalStackWorth(item, snapshot)
        if (totalWorth <= 0.0) return item

        val clone = item.clone()
        val meta = clone.itemMeta ?: return item
        val lore = meta.lore()?.toMutableList() ?: mutableListOf()
        lore.removeIf { line ->
            val raw = MessageUtil.serialize(line)
            raw.contains(WORTH_LORE_MARKER) ||
                raw.contains(LEGACY_WORTH_LORE_MARKER) ||
                (prefixLegacy.isNotEmpty() && raw.contains(prefixLegacy))
        }

        val line = format.replace("%price%", MessageUtil.formatMoney(totalWorth))
        lore.add(MessageUtil.deserialize(WORTH_LORE_MARKER + line))
        meta.lore(lore)
        clone.itemMeta = meta
        return clone
    }

    private fun totalStackWorth(item: ItemStack, snapshot: Snapshot): Double {
        val basePrice = plugin.pricesManager.getPrice(item.type)
        val category = plugin.pricesManager.getCategory(item.type)
        val multiplier = multiplierFor(category, snapshot)
        val perItemBase = if (basePrice > 0.0) basePrice * multiplier else 0.0
        val perItemShulker = if (isShulkerBox(item.type)) shulkerContentsWorth(item, snapshot) else 0.0
        val perItemTotal = perItemBase + perItemShulker
        return perItemTotal * item.amount.toDouble()
    }

    private fun shulkerContentsWorth(item: ItemStack, snapshot: Snapshot): Double {
        val meta = item.itemMeta as? BlockStateMeta ?: return 0.0
        val state = meta.blockState as? ShulkerBox ?: return 0.0
        var total = 0.0
        for (stack in state.inventory.contents) {
            if (stack == null || stack.type == Material.AIR || stack.amount <= 0) continue
            val price = plugin.pricesManager.getPrice(stack.type)
            if (price <= 0.0) continue
            val category = plugin.pricesManager.getCategory(stack.type)
            val multiplier = multiplierFor(category, snapshot)
            total += price * stack.amount.toDouble() * multiplier
        }
        return total
    }

    private fun isShulkerBox(material: Material): Boolean =
        material.name.contains("SHULKER_BOX")

    private fun updateSnapshot(player: Player) {
        val allowOverlay = plugin.config.getBoolean("settings.worth-lore.enabled", false) &&
            isAvailable() &&
            player.gameMode != GameMode.CREATIVE
        val title = MessageUtil.serialize(player.openInventory.title())
        val skipOverlay = shouldSkipTitle(title)
        val data = plugin.playerDataManager.getPlayerData(player.uniqueId)
        val multipliers = DoubleArray(Category.size) { idx ->
            val category = Category.fromId(idx)
            if (category != null) data.getMultiplier(category) else 1.0
        }
        snapshots[player.uniqueId] = Snapshot(
            allowOverlay = allowOverlay,
            skipOverlay = skipOverlay,
            multipliers = multipliers,
        )
    }

    private fun shouldSkipTitle(title: String): Boolean {
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

    private fun multiplierFor(category: Category?, snapshot: Snapshot): Double {
        if (category == null) return 1.0
        val id = category.id
        if (id < 0 || id >= snapshot.multipliers.size) return 1.0
        return snapshot.multipliers[id]
    }

    private fun formatPrefixLegacy(format: String): String {
        val prefix = format.substringBefore("%price%")
        if (prefix.isEmpty()) return ""
        return MessageUtil.legacy(prefix)
    }

    private fun runOnMain(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            plugin.server.scheduler.runTask(plugin, Runnable { action() })
        }
    }

    private companion object {
        private const val WORTH_LORE_MARKER = "\u00A7x\u00A7w\u00A7o\u00A7r\u00A7t\u00A7h"
        private const val LEGACY_WORTH_LORE_MARKER =
            "\u00C2\u00A7x\u00C2\u00A7w\u00C2\u00A7o\u00C2\u00A7r\u00C2\u00A7t\u00C2\u00A7h"
    }
}



