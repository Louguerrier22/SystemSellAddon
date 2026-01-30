package ca.l1cked.dsellsystem.gui

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.category.Category
import ca.l1cked.dsellsystem.util.MessageUtil
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryCreativeEvent

class ProgressGUI(private val plugin: DSellSystemPlugin) : Listener {
    private val openInventories: MutableMap<UUID, String> = HashMap()

    fun openCategoryGUI(player: Player, category: Category) {
        val title = plugin.guiManager.getProgressGUITitle(category.key)
        val inventory = Bukkit.createInventory(null, 54, MessageUtil.deserialize(title))

        val data = plugin.playerDataManager.getPlayerData(player.uniqueId)
        val progress = data.getProgress(category)
        val multiplier = data.getMultiplier(category)

        val filler = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val fillerMeta = filler.itemMeta
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty())
            filler.itemMeta = fillerMeta
        }
        for (i in 0 until 54) {
            inventory.setItem(i, filler)
        }

        inventory.setItem(1, createCategoryIcon(category, multiplier, progress))

        val levelPrices = plugin.config.getDoubleList("level-prices")
        val currentLevel = getCurrentLevel(multiplier)
        val backSlot = plugin.guiManager.getBackButtonSlot()

        val levelSlots = intArrayOf(
            10, 19, 28, 37, 38, 39, 30, 21, 12, 13,
            14, 23, 32, 41, 42, 43, 34, 25, 16, 7,
        )

        for (i in 0 until min(levelSlots.size, levelPrices.size)) {
            val slot = levelSlots[i]
            val required = levelPrices[i]
            val state = when {
                i < currentLevel -> "COMPLETE"
                i == currentLevel -> "WORKING"
                else -> "INCOMPLETE"
            }
            inventory.setItem(slot, createLevelIndicator(state, i, progress, multiplier, required))
        }

        inventory.setItem(backSlot, createBackButton())
        openInventories[player.uniqueId] = MessageUtil.legacy(title)
        player.openInventory(inventory)
    }

    private fun createLevelIndicator(
        state: String,
        levelIndex: Int,
        progress: Double,
        multiplier: Double,
        required: Double,
    ): ItemStack {
        val material = plugin.guiManager.getProgressItemMaterial(state)
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val name = plugin.guiManager.getProgressItemName(state)
        meta.displayName(MessageUtil.deserialize(name))

        var percent = 0.0
        val currentLevel = getCurrentLevel(multiplier)
        if (levelIndex < currentLevel) {
            percent = 100.0
        } else if (levelIndex == currentLevel) {
            percent = min((progress / required) * 100.0, 100.0)
        }

        val lore = ArrayList<Component>()
        val template = plugin.guiManager.getProgressItemLore(state)
        val progressBar = plugin.guiManager.buildProgressBar(percent)
        val base = plugin.config.getDouble("settings.base-multiplier", 1.0)
        val increment = plugin.config.getDouble("settings.multiplier-increment", 0.1)
        val nextMultiplier = base + (levelIndex + 1) * increment

        for (line in template) {
            var out = line
                .replace("%progress-bar%", progressBar)
                .replace("%multiplier%", String.format("%.1f", nextMultiplier))
                .replace("%progress%", String.format("%.0f", percent))

            out = if (levelIndex == currentLevel) {
                out.replace("%current-spent%", MessageUtil.formatMoney(progress))
            } else if (levelIndex < currentLevel) {
                out.replace("%current-spent%", MessageUtil.formatMoney(required))
            } else {
                out.replace("%current-spent%", "0")
            }

            out = out.replace("%needing-spent%", MessageUtil.formatMoney(required))
            lore.add(MessageUtil.deserialize(out))
        }
        meta.lore(lore)

        item.itemMeta = meta
        return item
    }

    private fun getRequiredProgressForLevel(multiplier: Double): Double {
        val level = getCurrentLevel(multiplier)
        val prices = plugin.config.getDoubleList("level-prices")
        return when {
            level < prices.size -> prices[level]
            prices.isEmpty() -> 1000000.0
            else -> prices[prices.size - 1]
        }
    }

    private fun getCurrentLevel(multiplier: Double): Int {
        val base = plugin.config.getDouble("settings.base-multiplier", 1.0)
        val increment = plugin.config.getDouble("settings.multiplier-increment", 0.1)
        val level = Math.round((multiplier - base) / increment).toInt()
        return max(0, level)
    }

    private fun createBackButton(): ItemStack {
        val material = plugin.guiManager.getBackButtonMaterial()
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val name = plugin.guiManager.getBackButtonName()
        meta.displayName(MessageUtil.deserialize(name))

        val lore = ArrayList<Component>()
        for (line in plugin.guiManager.getBackButtonLore()) {
            lore.add(MessageUtil.deserialize(line))
        }
        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun createCategoryIcon(category: Category, multiplier: Double, progress: Double): ItemStack {
        val material = plugin.guiManager.getProgressIconMaterial(category.key)
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val name = plugin.guiManager.getProgressIconName(category.key)
        meta.displayName(MessageUtil.deserialize(name))

        val lore = ArrayList<Component>()
        for (line in plugin.guiManager.getProgressIconLore(category.key)) {
            lore.add(MessageUtil.deserialize(line))
        }
        meta.lore(lore)

        for (flag in plugin.guiManager.getProgressIconFlags(category.key)) {
            meta.addItemFlags(flag)
        }

        val customModelData = plugin.guiManager.getProgressIconCustomModelData(category.key)
        if (customModelData > 0) {
            applyCustomModelData(meta, customModelData)
        }

        for (entry in plugin.guiManager.getProgressIconEnchantments(category.key)) {
            try {
                val parts = entry.split(":")
                if (parts.size != 2) continue
                val enchant = parseEnchantment(parts[0]) ?: continue
                val level = parts[1].toInt()
                meta.addEnchant(enchant, level, true)
            } catch (_: NumberFormatException) {
                // ignore invalid enchantments
            }
        }

        item.itemMeta = meta
        return item
    }



    @EventHandler
    fun onCreative(event: InventoryCreativeEvent) {
        val title = MessageUtil.serialize(event.view.title())
        if (!openInventories.containsKey(event.whoClicked.uniqueId)) return
        val expectedTitle = openInventories[event.whoClicked.uniqueId]
        if (expectedTitle != title) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        plugin.server.scheduler.runTask(plugin, Runnable {
            player.updateInventory()
        })
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = MessageUtil.serialize(event.view.title())
        if (!openInventories.containsKey(player.uniqueId)) return
        val expectedTitle = openInventories[player.uniqueId]
        if (expectedTitle != title) return
        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = MessageUtil.serialize(event.view.title())
        if (!openInventories.containsKey(player.uniqueId)) return
        val expectedTitle = openInventories[player.uniqueId]
        if (expectedTitle != title) return

        event.isCancelled = true
        val rawSlot = event.rawSlot
        if (rawSlot < 54) {
            val item = event.currentItem
            if (item != null && item.type != Material.AIR) {
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
            }
        }

        val backSlot = plugin.guiManager.getBackButtonSlot()
        if (rawSlot == backSlot) {
            player.closeInventory()
            plugin.sellGUI.openGUI(player)
            openInventories.remove(player.uniqueId)
        }
    }

    private fun parseEnchantment(raw: String): Enchantment? {
        val lowered = raw.lowercase()
        val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
        val vanilla = registry.get(NamespacedKey.minecraft(lowered))
        if (vanilla != null) return vanilla
        val namespaced = NamespacedKey.fromString(lowered)
        if (namespaced != null) {
            val byKey = registry.get(namespaced)
            if (byKey != null) return byKey
        }
        return registry.firstOrNull { it.key.key.equals(lowered, ignoreCase = true) }
    }

    private fun applyCustomModelData(meta: ItemMeta, customModelData: Int) {
        val component = meta.customModelDataComponent
        component.floats = listOf(customModelData.toFloat())
        meta.setCustomModelDataComponent(component)
    }
}
