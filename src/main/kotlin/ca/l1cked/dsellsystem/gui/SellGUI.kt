package ca.l1cked.dsellsystem.gui

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.api.event.DSellPayoutEvent
import ca.l1cked.dsellsystem.api.event.DSellPayoutSummaryEvent
import ca.l1cked.dsellsystem.category.Category
import ca.l1cked.dsellsystem.util.MessageUtil
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.ItemMeta
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryCreativeEvent

class SellGUI(private val plugin: DSellSystemPlugin) : Listener {
    private val openInventories: MutableMap<UUID, Inventory> = HashMap()

    fun openGUI(player: Player) {
        val title = plugin.guiManager.getSellGUITitle()
        val size = plugin.guiManager.getSellGUISize()
        val inventory = Bukkit.createInventory(null, size, MessageUtil.deserialize(title))
        for (category in Category.values()) {
            val slot = plugin.guiManager.getCategorySlot(category.key)
            inventory.setItem(slot, createCategoryIcon(player, category))
        }
        openInventories[player.uniqueId] = inventory
        player.openInventory(inventory)
    }

    private fun createCategoryIcon(player: Player, category: Category): ItemStack {
        val material = plugin.guiManager.getCategoryIconMaterial(category.key)
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val data = plugin.playerDataManager.getPlayerData(player.uniqueId)
        val multiplier = data.getMultiplier(category)
        val progress = data.getProgress(category)
        val progressPercent = getProgressPercentage(progress, multiplier)

        val name = plugin.guiManager.getCategoryIconName(category.key)
        meta.displayName(MessageUtil.deserialize(name))

        val lore = ArrayList<net.kyori.adventure.text.Component>()
        val template = plugin.guiManager.getCategoryIconLore(category.key)
        val nextMultiplier = multiplier + plugin.config.getDouble("settings.multiplier-increment", 0.1)
        for (line in template) {
            var out = line
                .replace("%multiplier%", String.format("%.1f", nextMultiplier))
                .replace("%progress%", String.format("%.1f%%", progressPercent))
                .replace("%progress-bar%", plugin.guiManager.buildProgressBar(progressPercent))
            lore.add(MessageUtil.deserialize(out))
        }
        meta.lore(lore)

        for (flag in plugin.guiManager.getCategoryIconFlags(category.key)) {
            meta.addItemFlags(flag)
        }

        val customModelData = plugin.guiManager.getCategoryIconCustomModelData(category.key)
        if (customModelData > 0) {
            applyCustomModelData(meta, customModelData)
        }

        for (entry in plugin.guiManager.getCategoryIconEnchantments(category.key)) {
            try {
                val parts = entry.split(":")
                if (parts.size != 2) continue
                val enchant = parseEnchantment(parts[0]) ?: continue
                val level = parts[1].toInt()
                meta.addEnchant(enchant, level, true)
            } catch (_: Exception) {
                // ignore invalid enchantments
            }
        }

        item.itemMeta = meta
        return item
    }

    private fun getProgressPercentage(progress: Double, multiplier: Double): Double {
        val required = getRequiredProgress(multiplier)
        return min((progress / required) * 100.0, 100.0)
    }

    private fun getRequiredProgress(multiplier: Double): Double {
        val base = plugin.config.getDouble("settings.base-multiplier", 1.0)
        val increment = plugin.config.getDouble("settings.multiplier-increment", 0.1)
        val level = Math.round((multiplier - base) / increment).toInt()
        val prices = plugin.config.getIntegerList("level-prices")
        if (prices.isEmpty()) return 500000.0
        return if (level < prices.size) prices[level].toDouble() else prices[prices.size - 1].toDouble()
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = plugin.guiManager.getSellGUITitle()
        val viewTitle = MessageUtil.serialize(event.view.title())
        if (viewTitle != MessageUtil.legacy(title)) return
        if (!openInventories.containsKey(player.uniqueId)) return

        val topSize = event.view.topInventory.size
        val rawSlot = event.rawSlot
        if (rawSlot < topSize) {
            for (category in Category.values()) {
                val slot = plugin.guiManager.getCategorySlot(category.key)
                if (rawSlot == slot) {
                    event.isCancelled = true
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
                    plugin.progressGUI.openCategoryGUI(player, category)
                    return
                }
            }
            if (rawSlot !in 0..35) {
                event.isCancelled = true
                return
            }
            return
        }
    }


    @EventHandler
    fun onCreative(event: InventoryCreativeEvent) {
        val title = plugin.guiManager.getSellGUITitle()
        val viewTitle = MessageUtil.serialize(event.view.title())
        if (viewTitle != MessageUtil.legacy(title)) return
        if (!openInventories.containsKey(event.whoClicked.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = plugin.guiManager.getSellGUITitle()
        val viewTitle = MessageUtil.serialize(event.view.title())
        if (viewTitle != MessageUtil.legacy(title)) return
        if (!openInventories.containsKey(player.uniqueId)) return

        val topSize = event.view.topInventory.size
        for (slot in event.rawSlots) {
            if (slot < topSize && slot !in 0..35) {
                event.isCancelled = true
                return
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (!openInventories.containsKey(player.uniqueId)) return
        val title = plugin.guiManager.getSellGUITitle()
        val viewTitle = MessageUtil.serialize(event.view.title())
        if (viewTitle != MessageUtil.legacy(title)) return

        val inventory = openInventories[player.uniqueId] ?: return
        if (!plugin.config.getBoolean("settings.enabled", true)) {
            for (slot in 0 until 36) {
                val item = inventory.getItem(slot) ?: continue
                if (item.type == Material.AIR) continue
                player.inventory.addItem(item)
            }
            MessageUtil.sendMessage(player, plugin.config.getString("messages.disabled", "&cDSellSystem is currently disabled.") ?: "&cDSellSystem is currently disabled.")
            openInventories.remove(player.uniqueId)
            return
        }
        var total = 0.0
        val progressTotals: MutableMap<Category, Double> = HashMap()
        val itemsSold: MutableMap<String, Int> = HashMap()
        val soldItemsForHistory = mutableListOf<ItemStack>()
        val hasDetailListeners = DSellPayoutEvent.hasListeners()
        val categoryTotals = DoubleArray(Category.size)

        for (slot in 0 until 36) {
            val item = inventory.getItem(slot) ?: continue
            if (item.type == Material.AIR) continue
            val pricePerItem = getItemBasePrice(item)
            if (pricePerItem <= 0) {
                player.inventory.addItem(item)
                continue
            }
            val category = getItemCategory(item.type) ?: run {
                player.inventory.addItem(item)
                continue
            }

            val data = plugin.playerDataManager.getPlayerData(player.uniqueId)

            if (isShulkerBox(item.type)) {
                if (pricePerItem > 0.0) {
                    soldItemsForHistory.add(item.clone())
                    val multiplier = data.getMultiplier(category)
                    val amount = item.amount
                    val itemTotal = pricePerItem * amount * multiplier
                    total += itemTotal
                    if (hasDetailListeners) {
                        categoryTotals[category.id] += itemTotal
                    }
                    val currentProgress = progressTotals[category] ?: 0.0
                    progressTotals[category] = currentProgress + (pricePerItem * amount)
                }

                for (content in shulkerContents(item)) {
                    val contentPrice = getItemBasePrice(content)
                    if (contentPrice <= 0.0) continue
                    val contentCategory = getItemCategory(content.type) ?: continue
                    val contentMultiplier = data.getMultiplier(contentCategory)
                    val contentBase = contentPrice * content.amount
                    val contentTotal = contentBase * contentMultiplier
                    total += contentTotal
                    if (hasDetailListeners) {
                        categoryTotals[contentCategory.id] += contentTotal
                    }
                    val currentProgress = progressTotals[contentCategory] ?: 0.0
                    progressTotals[contentCategory] = currentProgress + contentBase
                }
                continue
            }

            val multiplier = data.getMultiplier(category)
            val amount = item.amount
            val itemTotal = pricePerItem * amount * multiplier
            total += itemTotal
            if (hasDetailListeners) {
                categoryTotals[category.id] += itemTotal
            }

            val currentProgress = progressTotals[category] ?: 0.0
            progressTotals[category] = currentProgress + (pricePerItem * amount)

            val name = item.type.name
            val currentCount = itemsSold[name] ?: 0
            itemsSold[name] = currentCount + amount
            soldItemsForHistory.add(item.clone())
        }

        if (total > 0) {
            plugin.economy.depositPlayer(player, total)
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)

            val soldTotal = plugin.config.getString("messages.sold-total", "#00f900+$%amount%")
            if (!soldTotal.isNullOrEmpty()) {
                val msg = soldTotal.replace("%amount%", MessageUtil.formatMoney(total))
                MessageUtil.sendMessage(player, msg)
            }

            val actionBar = plugin.config.getString("messages.sold-total-action-bar", "")
            if (!actionBar.isNullOrEmpty()) {
                val msg = actionBar.replace("%amount%", MessageUtil.formatMoney(total))
                MessageUtil.sendActionBar(player, msg)
            }

            val showBreakdown = plugin.config.getBoolean("message-settings.show-item-breakdown", false)
            if (showBreakdown) {
                for ((materialName, quantity) in itemsSold) {
                    val itemName = materialName.replace("_", " ").lowercase()
                    val template = plugin.config.getString(
                        "messages.item-sold",
                        "&a+$%amount% &7(%item% x%quantity%)",
                    ) ?: ""
                    val itemPrice = getItemBasePrice(ItemStack(Material.valueOf(materialName)))
                    val line = template
                        .replace("%amount%", String.format("%.2f", itemPrice * quantity))
                        .replace("%item%", itemName)
                        .replace("%quantity%", quantity.toString())
                    MessageUtil.sendMessage(player, line)
                }
            }

            val data = plugin.playerDataManager.getPlayerData(player.uniqueId)
            for ((category, progress) in progressTotals) {
                data.addProgress(category, progress)
                var currentProgress = data.getProgress(category)
                var currentMultiplier = data.getMultiplier(category)
                var required = getRequiredProgress(currentMultiplier)
                val maxMultiplier = plugin.config.getDouble("settings.max-multiplier", 2.1)
                val increment = plugin.config.getDouble("settings.multiplier-increment", 0.1)
                while (currentProgress >= required && currentMultiplier < maxMultiplier) {
                    data.setProgress(category, currentProgress - required)
                    data.setMultiplier(category, currentMultiplier + increment)
                    var message = plugin.config.getString("messages.multiplier-upgrade", "")
                    if (!message.isNullOrEmpty()) {
                        message = message
                            .replace("%category%", category.displayName)
                            .replace("%multiplier%", String.format("%.1f", currentMultiplier + increment))
                        MessageUtil.sendMessage(player, message)
                    }
                    currentMultiplier = data.getMultiplier(category)
                    currentProgress = data.getProgress(category)
                    required = getRequiredProgress(currentMultiplier)
                }
            }

            plugin.playerDataManager.savePlayerData(player.uniqueId)

            val timestamp = System.currentTimeMillis()
            val historyEnabled = plugin.config.getBoolean("history.enabled", false)
            val paidByCategory = linkedMapOf<String, Double>()
            for (i in 0 until Category.size) {
                val amount = categoryTotals[i]
                if (amount <= 0.0) continue
                val key = Category.keyForId(i) ?: continue
                paidByCategory[key] = amount
            }
            val txId = if (historyEnabled) {
                plugin.sellStorage.recordSell(
                    playerUuid = player.uniqueId,
                    playerName = player.name,
                    timestampMillis = timestamp,
                    personalMultiplier = 1.0,
                    paidByCategory = paidByCategory,
                    totalPaid = total,
                )
            } else {
                null
            }
            if (historyEnabled && txId != null && soldItemsForHistory.isNotEmpty()) {
                plugin.ioScope.launch {
                    plugin.sellStorage.recordSellItems(txId, soldItemsForHistory)
                }
            }
            if (DSellPayoutSummaryEvent.hasListeners()) {
                Bukkit.getPluginManager().callEvent(
                    DSellPayoutSummaryEvent(
                        player = player,
                        personalMultiplier = 1.0,
                        totalPaid = total,
                        transactionId = txId,
                        timestampMillis = timestamp,
                    )
                )
            }

            if (hasDetailListeners) {
                val (ids, paid) = buildCategoryPayload(categoryTotals)
                Bukkit.getPluginManager().callEvent(
                    DSellPayoutEvent(
                        player = player,
                        categoryIds = ids,
                        paidByCategory = paid,
                        personalMultiplier = 1.0,
                        totalPaid = total,
                        transactionId = txId,
                        timestampMillis = timestamp,
                    )
                )
            }

            notifyMayorCallback(
                playerId = player.uniqueId,
                categoryTotals = categoryTotals,
                totalPaid = total,
                timestampMillis = timestamp,
                txId = txId,
            )
        }

        openInventories.remove(player.uniqueId)
    }

    private fun getItemBasePrice(item: ItemStack): Double {
        return plugin.pricesManager.getPrice(item.type)
    }

    private fun shulkerContents(item: ItemStack): List<ItemStack> {
        val meta = item.itemMeta as? BlockStateMeta ?: return emptyList()
        val state = meta.blockState as? org.bukkit.block.ShulkerBox ?: return emptyList()
        return state.inventory.contents.filterNotNull().filter { it.amount > 0 }
    }

    private fun isShulkerBox(material: Material): Boolean =
        material.name.contains("SHULKER_BOX")


    private fun getItemCategory(material: Material): Category? =
        plugin.pricesManager.getCategory(material)

    private fun parseEnchantment(name: String): Enchantment? {
        val lowered = name.lowercase()
        val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
        val vanilla = registry.get(NamespacedKey.minecraft(lowered))
        if (vanilla != null) return vanilla
        val namespaced = NamespacedKey.fromString(lowered)
        if (namespaced != null) {
            val byKey = registry.get(namespaced)
            if (byKey != null) return byKey
        }
        return registry.firstOrNull {
            it.key.key.equals(lowered, ignoreCase = true) ||
                it.key.toString().equals(lowered, ignoreCase = true)
        }
    }

    private fun applyCustomModelData(meta: ItemMeta, customModelData: Int) {
        val component = meta.customModelDataComponent
        component.floats = listOf(customModelData.toFloat())
        meta.setCustomModelDataComponent(component)
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

    private fun notifyMayorCallback(
        playerId: UUID,
        categoryTotals: DoubleArray?,
        totalPaid: Double,
        timestampMillis: Long,
        txId: Long?,
    ) {
        if (totalPaid <= 0.0) return
        if (!plugin.mayorBridge.isAvailable()) {
            plugin.mayorBridge.refreshProvider()
            if (!plugin.mayorBridge.isAvailable()) return
        }
        val payload = DoubleArray(Category.size + 1)
        if (categoryTotals != null && categoryTotals.size == Category.size) {
            for (i in 0 until Category.size) {
                payload[i] = categoryTotals[i]
            }
        }
        payload[Category.size] = totalPaid
        plugin.mayorBridge.notifyPayout(
            playerId = playerId,
            paidByCategory = payload,
            totalPaid = totalPaid,
            timestampMillis = timestampMillis,
            transactionId = txId?.toString(),
        )
    }
}
