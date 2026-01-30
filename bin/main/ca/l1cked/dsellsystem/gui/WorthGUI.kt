package ca.l1cked.dsellsystem.gui

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.util.MessageUtil
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryCreativeEvent

class WorthGUI(private val plugin: DSellSystemPlugin) : Listener {
    private val playerStates: MutableMap<UUID, PlayerGUIState> = HashMap()
    private val itemsPerPage = 45

    fun openGUI(player: Player, page: Int) {
        val state = playerStates.computeIfAbsent(player.uniqueId) { PlayerGUIState() }
        state.page = page

        val allPrices = plugin.pricesManager.getAllPrices()
        var entries = ArrayList(allPrices.entries)
        entries = ArrayList(applyFilter(entries, state.filterOption))
        entries = ArrayList(applySort(entries, state.sortOption))

        var totalPages = ceil(entries.size / itemsPerPage.toDouble()).toInt()
        if (totalPages == 0) totalPages = 1

        var currentPage = page
        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1
        state.page = currentPage

        val start = (currentPage - 1) * itemsPerPage
        val end = min(start + itemsPerPage, entries.size)

        val title = plugin.guiManager.getWorthGUITitle()
            .replace("%page%", currentPage.toString())
            .replace("%total%", totalPages.toString())

        val inventory = Bukkit.createInventory(null, 54, MessageUtil.deserialize(title))
        for (i in start until end) {
            val entry = entries[i]
            val item = createPriceItem(entry.key, entry.value)
            inventory.setItem(i - start, item)
        }

        if (currentPage > 1) {
            inventory.setItem(45, createNavigationButton(true))
        }
        inventory.setItem(48, createSortButton(state.sortOption))
        inventory.setItem(49, createRefreshButton())
        inventory.setItem(50, createFilterButton(state.filterOption))
        if (currentPage < totalPages) {
            inventory.setItem(53, createNavigationButton(false))
        }

        player.openInventory(inventory)
    }

    private fun createPriceItem(material: Material, price: Double): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val lore = ArrayList<Component>()
        for (line in plugin.guiManager.getWorthItemLore()) {
            lore.add(MessageUtil.deserialize(line.replace("%price%", MessageUtil.formatMoney(price))))
        }
        meta.lore(lore)
        meta.addItemFlags(
            ItemFlag.HIDE_ATTRIBUTES,
            ItemFlag.HIDE_ENCHANTS,
            ItemFlag.HIDE_DESTROYS,
            ItemFlag.HIDE_PLACED_ON,
            ItemFlag.HIDE_UNBREAKABLE,
        )
        item.itemMeta = meta
        return item
    }

    private fun formatItemName(name: String): String {
        val parts = name.lowercase().split("_")
        val sb = StringBuilder()
        for (part in parts) {
            if (sb.isNotEmpty()) sb.append("")
            sb.append(toTinyFont(capitalize(part)))
        }
        return sb.toString()
    }

    private fun capitalize(value: String): String {
        if (value.isEmpty()) return value
        return value.substring(0, 1).uppercase() + value.substring(1)
    }

    private fun toTinyFont(value: String): String {
        val sb = StringBuilder()
        for (c in value.toCharArray()) {
            sb.append(tinyFontMap[c] ?: c)
        }
        return sb.toString()
    }

    private fun createSortButton(option: SortOption): ItemStack {
        val material = plugin.guiManager.getWorthSortButtonMaterial()
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val name = plugin.guiManager.getWorthSortButtonName()
        meta.displayName(MessageUtil.deserialize(name))

        val lore = ArrayList<Component>()
        val selected = plugin.guiManager.getWorthLoreSelected()
        val unselected = plugin.guiManager.getWorthLoreUnselected()
        for (opt in SortOption.values()) {
            if (opt == option) {
                lore.add(MessageUtil.deserialize(selected.replace("%option%", opt.display)))
            } else {
                lore.add(MessageUtil.deserialize(unselected.replace("%option%", opt.display)))
            }
        }
        meta.lore(lore)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun createRefreshButton(): ItemStack {
        val material = plugin.guiManager.getWorthRefreshButtonMaterial()
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val name = plugin.guiManager.getWorthRefreshButtonName()
        meta.displayName(MessageUtil.deserialize(name))
        meta.lore(MessageUtil.colorizeList(plugin.guiManager.getWorthRefreshButtonLore()))
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun createFilterButton(option: FilterOption): ItemStack {
        val material = plugin.guiManager.getWorthFilterButtonMaterial()
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val name = plugin.guiManager.getWorthFilterButtonName()
        meta.displayName(MessageUtil.deserialize(name))

        val lore = ArrayList<Component>()
        val selected = plugin.guiManager.getWorthFilterLoreSelected()
        val unselected = plugin.guiManager.getWorthFilterLoreUnselected()
        for (opt in FilterOption.values()) {
            if (opt == option) {
                lore.add(MessageUtil.deserialize(selected.replace("%option%", opt.display)))
            } else {
                lore.add(MessageUtil.deserialize(unselected.replace("%option%", opt.display)))
            }
        }
        meta.lore(lore)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun createNavigationButton(previous: Boolean): ItemStack {
        val material = plugin.guiManager.getWorthNavigationMaterial()
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val name = if (previous) {
            plugin.guiManager.getWorthPreviousButtonName()
        } else {
            plugin.guiManager.getWorthNextButtonName()
        }
        val lore = if (previous) {
            plugin.guiManager.getWorthPreviousButtonLore()
        } else {
            plugin.guiManager.getWorthNextButtonLore()
        }

        meta.displayName(MessageUtil.deserialize(name))
        meta.lore(MessageUtil.colorizeList(lore))
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun applyFilter(
        list: List<Map.Entry<Material, Double>>,
        option: FilterOption,
    ): List<Map.Entry<Material, Double>> {
        if (option == FilterOption.ALL) return list
        return list.filter { matchesFilter(it.key, option) }
    }

    private fun matchesFilter(material: Material, option: FilterOption): Boolean {
        return when (option) {
            FilterOption.ALL -> true
            FilterOption.BLOCKS -> material.isBlock
            FilterOption.TOOLS -> {
                val name = material.name
                name.contains("PICKAXE") ||
                    name.contains("AXE") ||
                    name.contains("SHOVEL") ||
                    name.contains("HOE") ||
                    name.contains("SHEARS")
            }
            FilterOption.FOOD -> material.isEdible
            FilterOption.COMBAT -> {
                val name = material.name
                name.contains("SWORD") ||
                    name.contains("BOW") ||
                    name.contains("ARROW") ||
                    name.contains("ARMOR") ||
                    name.contains("HELMET") ||
                    name.contains("CHESTPLATE") ||
                    name.contains("LEGGINGS") ||
                    name.contains("BOOTS")
            }
            FilterOption.POTIONS -> {
                val name = material.name
                name.contains("POTION") || material == Material.BREWING_STAND || material == Material.CAULDRON
            }
            FilterOption.BOOKS -> {
                val name = material.name
                name.contains("BOOK") || material == Material.ENCHANTED_BOOK
            }
            FilterOption.INGREDIENTS -> {
                val name = material.name
                name.contains("INGOT") ||
                    name.contains("NUGGET") ||
                    name.contains("DUST") ||
                    name.contains("GEM") ||
                    material == Material.STICK ||
                    material == Material.STRING
            }
            FilterOption.UTILITIES -> {
                val name = material.name
                name.contains("BUCKET") ||
                    name.contains("TORCH") ||
                    material == Material.LADDER ||
                    material == Material.CHEST
            }
        }
    }

    private fun applySort(
        list: List<Map.Entry<Material, Double>>,
        option: SortOption,
    ): List<Map.Entry<Material, Double>> {
        val out = ArrayList(list)
        when (option) {
            SortOption.HIGHEST_PRICE -> out.sortByDescending { it.value }
            SortOption.LOWEST_PRICE -> out.sortBy { it.value }
            SortOption.NAME -> out.sortBy { it.key.name }
        }
        return out
    }



    @EventHandler
    fun onCreative(event: InventoryCreativeEvent) {
        val title = MessageUtil.serialize(event.view.title())
        val prefix = MessageUtil.legacy(plugin.guiManager.getWorthGUITitle().split("%")[0].trim())
        if (!title.contains(prefix)) return
        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!playerStates.containsKey(player.uniqueId)) return
        val title = MessageUtil.serialize(event.view.title())
        val prefix = MessageUtil.legacy(plugin.guiManager.getWorthGUITitle().split("%")[0].trim())
        if (!title.contains(prefix)) return
        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!playerStates.containsKey(player.uniqueId)) return

        val title = MessageUtil.serialize(event.view.title())
        val prefix = MessageUtil.legacy(plugin.guiManager.getWorthGUITitle().split("%")[0].trim())
        if (!title.contains(prefix)) return

        event.isCancelled = true
        val rawSlot = event.rawSlot
        if (rawSlot < 54) {
            val item = event.currentItem
            if (item != null && item.type != Material.AIR) {
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
            }
        }

        val state = playerStates[player.uniqueId] ?: return
        when (rawSlot) {
            48 -> {
                cycleSortOption(state)
                openGUI(player, state.page)
            }
            49 -> {
                plugin.pricesManager.reloadPrices()
                openGUI(player, state.page)
            }
            50 -> {
                cycleFilterOption(state)
                openGUI(player, 1)
            }
            45 -> {
                if (state.page > 1) {
                    openGUI(player, state.page - 1)
                }
            }
            53 -> {
                openGUI(player, state.page + 1)
            }
        }
    }

    private fun cycleSortOption(state: PlayerGUIState) {
        val values = SortOption.values()
        val next = (state.sortOption.ordinal + 1) % values.size
        state.sortOption = values[next]
    }

    private fun cycleFilterOption(state: PlayerGUIState) {
        val values = FilterOption.values()
        val next = (state.filterOption.ordinal + 1) % values.size
        state.filterOption = values[next]
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (!playerStates.containsKey(player.uniqueId)) return
        val title = MessageUtil.serialize(event.view.title())
        val prefix = MessageUtil.legacy(plugin.guiManager.getWorthGUITitle().split("%")[0].trim())
        if (!title.contains(prefix)) return
    }

    fun cleanup(player: Player) {
        playerStates.remove(player.uniqueId)
    }

    private class PlayerGUIState {
        var page: Int = 1
        var sortOption: SortOption = SortOption.NAME
        var filterOption: FilterOption = FilterOption.ALL
    }

    enum class SortOption(val display: String) {
        HIGHEST_PRICE("Highest Price"),
        LOWEST_PRICE("Lowest Price"),
        NAME("Name"),
    }

    enum class FilterOption(val display: String) {
        ALL("All"),
        BLOCKS("Blocks"),
        TOOLS("Tools"),
        FOOD("Food"),
        COMBAT("Combat"),
        POTIONS("Potions"),
        BOOKS("Books"),
        INGREDIENTS("Ingredients"),
        UTILITIES("Utilities"),
    }

    private companion object {
        private val tinyFontMap: Map<Char, Char> = mapOf(
            'A' to '\u1D00',
            'B' to '\u0299',
            'C' to '\u1D04',
            'D' to '\u1D05',
            'E' to '\u1D07',
            'F' to '\u0493',
            'G' to '\u0262',
            'H' to '\u029C',
            'I' to '\u026A',
            'J' to '\u1D0A',
            'K' to '\u1D0B',
            'L' to '\u029F',
            'M' to '\u1D0D',
            'N' to '\u0274',
            'O' to '\u1D0F',
            'P' to '\u1D18',
            'Q' to '\u01EB',
            'R' to '\u0280',
            'S' to 's',
            'T' to '\u1D1B',
            'U' to '\u1D1C',
            'V' to '\u1D20',
            'W' to '\u1D21',
            'X' to 'x',
            'Y' to '\u028F',
            'Z' to '\u1D22',
        )
    }
}
