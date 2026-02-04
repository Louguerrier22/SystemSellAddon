package ca.l1cked.systemselladdon.managers

import ca.l1cked.systemselladdon.SystemSellAddonPlugin
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemFlag
import java.io.File
import java.io.IOException
import java.nio.file.Files

class GUIManager(private val plugin: SystemSellAddonPlugin) {
    private lateinit var guiFile: File
    private lateinit var guiConfig: FileConfiguration

    init {
        loadGUI()
    }

    private fun loadGUI() {
        guiFile = File(plugin.dataFolder, "gui.yml")
        if (!guiFile.exists()) {
            guiFile.parentFile.mkdirs()
            try {
                plugin.getResource("gui.yml")?.use { input ->
                    Files.copy(input, guiFile.toPath())
                } ?: guiFile.createNewFile()
            } catch (e: IOException) {
                plugin.logger.severe("Could not create gui.yml!")
                e.printStackTrace()
            }
        }
        guiConfig = YamlConfiguration.loadConfiguration(guiFile)
    }

    fun getSellGUITitle(): String =
        guiConfig.getString("inventories.list-category-title", "Place items in here to sell") ?: ""

    fun getSellGUISize(): Int = 45

    fun getCategoryIconsStartSlot(): Int = 36

    fun areCategoryIconsEnabled(): Boolean = true

    fun getProgressGUITitle(categoryKey: String): String =
        guiConfig.getString("categories.$categoryKey.progress-title", "$categoryKey Progress") ?: ""

    fun getCategorySlot(categoryKey: String): Int =
        guiConfig.getInt("categories.$categoryKey.inventory-slot", 36)

    fun getBackButtonSlot(): Int =
        guiConfig.getInt("back-button.slot", 45)

    fun getBackButtonMaterial(): Material =
        parseMaterial(guiConfig.getString("back-button.material"), Material.RED_STAINED_GLASS_PANE)

    fun getBackButtonName(): String =
        guiConfig.getString("back-button.name", "&cBACK") ?: ""

    fun getBackButtonLore(): List<String> =
        guiConfig.getStringList("back-button.lore")

    fun getCategoryIconMaterial(categoryKey: String): Material =
        parseMaterial(guiConfig.getString("categories.$categoryKey.categories-icon.type"), Material.STONE)

    fun getCategoryIconName(categoryKey: String): String =
        guiConfig.getString("categories.$categoryKey.categories-icon.display-name", categoryKey) ?: categoryKey

    fun getCategoryIconLore(categoryKey: String): List<String> =
        guiConfig.getStringList("categories.$categoryKey.categories-icon.lore")

    fun getCategoryIconFlags(categoryKey: String): List<ItemFlag> =
        parseItemFlags(guiConfig.getStringList("categories.$categoryKey.categories-icon.flags"))

    fun getCategoryIconCustomModelData(categoryKey: String): Int =
        guiConfig.getInt("categories.$categoryKey.categories-icon.custom-model-data", 0)

    fun getCategoryIconEnchantments(categoryKey: String): List<String> =
        guiConfig.getStringList("categories.$categoryKey.categories-icon.enchantments")

    fun getProgressIconMaterial(categoryKey: String): Material =
        parseMaterial(guiConfig.getString("categories.$categoryKey.progress-icon.type"), Material.STONE)

    fun getProgressIconName(categoryKey: String): String =
        guiConfig.getString("categories.$categoryKey.progress-icon.display-name", categoryKey) ?: categoryKey

    fun getProgressIconLore(categoryKey: String): List<String> =
        guiConfig.getStringList("categories.$categoryKey.progress-icon.lore")

    fun getProgressIconFlags(categoryKey: String): List<ItemFlag> =
        parseItemFlags(guiConfig.getStringList("categories.$categoryKey.progress-icon.flags"))

    fun getProgressIconCustomModelData(categoryKey: String): Int =
        guiConfig.getInt("categories.$categoryKey.progress-icon.custom-model-data", 0)

    fun getProgressIconEnchantments(categoryKey: String): List<String> =
        guiConfig.getStringList("categories.$categoryKey.progress-icon.enchantments")

    fun getProgressItemMaterial(state: String): Material =
        parseMaterial(guiConfig.getString("progress-items.$state.type"), Material.YELLOW_STAINED_GLASS_PANE)

    fun getProgressItemName(state: String): String =
        guiConfig.getString("progress-items.$state.display-name", state) ?: state

    fun getProgressItemLore(state: String): List<String> =
        guiConfig.getStringList("progress-items.$state.lore")

    fun getFilledPrefix(): String =
        guiConfig.getString("progress-bar.filled-prefix", "#00f986&m") ?: ""

    fun getEmptyPrefix(): String =
        guiConfig.getString("progress-bar.empty-prefix", "&f&m") ?: ""

    fun getCompleteChar(): String =
        guiConfig.getString("progress-bar.complete-char", " ") ?: " "

    fun getIncompleteChar(): String =
        guiConfig.getString("progress-bar.incomplete-char", " ") ?: " "

    fun getMaxBars(): Int =
        guiConfig.getInt("progress-bar.max-bars", 16)

    fun buildProgressBar(percentage: Double): String {
        val maxBars = getMaxBars()
        val filled = kotlin.math.ceil((percentage / 100.0) * maxBars).toInt()
        val empty = maxBars - filled
        val sb = StringBuilder()
        sb.append(getFilledPrefix())
        repeat(filled) { sb.append(getCompleteChar()) }
        sb.append(getEmptyPrefix())
        repeat(empty) { sb.append(getIncompleteChar()) }
        sb.append("&r")
        return sb.toString()
    }

    fun getWorthGUITitle(): String =
        guiConfig.getString("worth-gui.title", "ITEM PRICES (Page %page%/%total%)") ?: ""

    fun getWorthSortButtonMaterial(): Material =
        parseMaterial(guiConfig.getString("worth-gui.sort-button.type"), Material.CAULDRON)

    fun getWorthSortButtonName(): String =
        guiConfig.getString("worth-gui.sort-button.display-name", "#00f986sort") ?: ""

    fun getWorthLoreSelected(): String =
        guiConfig.getString("worth-gui.sort-button.lore-selected", "#00f986• %option%") ?: ""

    fun getWorthLoreUnselected(): String =
        guiConfig.getString("worth-gui.sort-button.lore-unselected", "&f• %option%") ?: ""

    fun getWorthRefreshButtonMaterial(): Material =
        parseMaterial(guiConfig.getString("worth-gui.refresh-button.type"), Material.ANVIL)

    fun getWorthRefreshButtonName(): String =
        guiConfig.getString("worth-gui.refresh-button.display-name", "#00f986Item Prices") ?: ""

    fun getWorthRefreshButtonLore(): List<String> =
        guiConfig.getStringList("worth-gui.refresh-button.lore")

    fun getWorthFilterButtonMaterial(): Material =
        parseMaterial(guiConfig.getString("worth-gui.filter-button.type"), Material.HOPPER)

    fun getWorthFilterButtonName(): String =
        guiConfig.getString("worth-gui.filter-button.display-name", "#00f986Filter") ?: ""

    fun getWorthFilterLoreSelected(): String =
        guiConfig.getString("worth-gui.filter-button.lore-selected", "#00f986• %option%") ?: ""

    fun getWorthFilterLoreUnselected(): String =
        guiConfig.getString("worth-gui.filter-button.lore-unselected", "&f• %option%") ?: ""

    fun getWorthNavigationMaterial(): Material =
        parseMaterial(guiConfig.getString("worth-gui.previous-button.type"), Material.ARROW)

    fun getWorthPreviousButtonName(): String =
        guiConfig.getString("worth-gui.previous-button.display-name", "#00f986previous") ?: ""

    fun getWorthPreviousButtonLore(): List<String> =
        guiConfig.getStringList("worth-gui.previous-button.lore")

    fun getWorthNextButtonName(): String =
        guiConfig.getString("worth-gui.next-button.display-name", "#00f986next") ?: ""

    fun getWorthNextButtonLore(): List<String> =
        guiConfig.getStringList("worth-gui.next-button.lore")

    fun getWorthItemNameColor(): String =
        guiConfig.getString("worth-gui.item-display.name-color", "#00f986") ?: ""

    fun getWorthItemLore(): List<String> =
        guiConfig.getStringList("worth-gui.item-display.lore")

    fun reload() {
        guiConfig = YamlConfiguration.loadConfiguration(guiFile)
    }

    private fun parseMaterial(value: String?, fallback: Material): Material {
        if (value == null) return fallback
        return try {
            Material.valueOf(value)
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }

    private fun parseItemFlags(values: List<String>): List<ItemFlag> {
        val out = mutableListOf<ItemFlag>()
        for (name in values) {
            try {
                out.add(ItemFlag.valueOf(name))
            } catch (_: IllegalArgumentException) {
                // ignore
            }
        }
        return out
    }
}



