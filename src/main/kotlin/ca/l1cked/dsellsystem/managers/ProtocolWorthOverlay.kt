package ca.l1cked.dsellsystem.managers

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.util.MessageUtil
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

class ProtocolWorthOverlay(private val plugin: DSellSystemPlugin) {
    private val protocolManager = ProtocolLibrary.getProtocolManager()
    private var listener: PacketAdapter? = null

    fun isAvailable(): Boolean =
        plugin.server.pluginManager.getPlugin("ProtocolLib")?.isEnabled == true

    fun isEnabled(): Boolean =
        plugin.config.getBoolean("settings.worth-lore.enabled", false) && isAvailable()

    fun enable() {
        if (!isAvailable() || listener != null) return
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
    }

    fun disable() {
        listener?.let { protocolManager.removePacketListener(it) }
        listener = null
    }

    fun refreshPlayer(player: Player) {
        player.updateInventory()
    }

    private fun applyWorth(player: Player, item: ItemStack?): ItemStack? {
        if (item == null || item.type == Material.AIR) return item
        val format = plugin.config.getString("settings.worth-lore.format", "&fWorth: &a$%price%")
            ?: ""
        if (format.isEmpty()) return item

        val totalWorth = totalStackWorth(item, player)
        if (totalWorth <= 0.0) return item

        val clone = item.clone()
        val meta = clone.itemMeta ?: return item
        val lore = meta.lore()?.toMutableList() ?: mutableListOf()
        lore.removeIf { line ->
            val raw = MessageUtil.serialize(line)
            raw.contains(WORTH_LORE_MARKER) || raw.contains("Worth:")
        }

        val line = format.replace("%price%", MessageUtil.formatMoney(totalWorth))
        lore.add(MessageUtil.deserialize(WORTH_LORE_MARKER + line))
        meta.lore(lore)
        clone.itemMeta = meta
        return clone
    }

    private fun totalStackWorth(item: ItemStack, player: Player): Double {
        val data = plugin.playerDataManager.getPlayerData(player.uniqueId)
        val basePrice = plugin.pricesManager.getPrice(item.type)
        val category = plugin.pricesManager.getCategory(item.type)
        val multiplier = if (category != null) data.getMultiplier(category) else 1.0
        val perItemBase = if (basePrice > 0.0) basePrice * multiplier else 0.0
        val perItemShulker = if (isShulkerBox(item.type)) shulkerContentsWorth(item, data) else 0.0
        val perItemTotal = perItemBase + perItemShulker
        return perItemTotal * item.amount.toDouble()
    }

    private fun shulkerContentsWorth(item: ItemStack, data: ca.l1cked.dsellsystem.data.PlayerData): Double {
        val meta = item.itemMeta as? BlockStateMeta ?: return 0.0
        val state = meta.blockState as? ShulkerBox ?: return 0.0
        var total = 0.0
        for (stack in state.inventory.contents) {
            if (stack == null || stack.type == Material.AIR || stack.amount <= 0) continue
            val price = plugin.pricesManager.getPrice(stack.type)
            if (price <= 0.0) continue
            val category = plugin.pricesManager.getCategory(stack.type)
            val multiplier = if (category != null) data.getMultiplier(category) else 1.0
            total += price * stack.amount.toDouble() * multiplier
        }
        return total
    }

    private fun isShulkerBox(material: Material): Boolean =
        material.name.contains("SHULKER_BOX")

    private companion object {
        private const val WORTH_LORE_MARKER = "§x§w§o§r§t§h"
    }
}
