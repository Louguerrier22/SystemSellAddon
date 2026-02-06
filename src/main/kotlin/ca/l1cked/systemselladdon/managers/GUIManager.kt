package ca.l1cked.systemselladdon.managers

import ca.l1cked.systemselladdon.SystemSellAddonPlugin
import org.bukkit.Material
import org.bukkit.inventory.ItemFlag

class GUIManager(private val plugin: SystemSellAddonPlugin) {
    private data class IconSpec(
        val material: Material,
        val name: String,
        val lore: List<String>,
        val flags: List<ItemFlag> = emptyList(),
        val customModelData: Int = 0,
        val enchantments: List<String> = emptyList(),
    )

    private data class CategorySpec(
        val slot: Int,
        val progressTitle: String,
        val categoryIcon: IconSpec,
        val progressIcon: IconSpec,
    )

    private data class ProgressItemSpec(
        val material: Material,
        val name: String,
        val lore: List<String>,
    )

    private data class ButtonSpec(
        val slot: Int,
        val material: Material,
        val name: String,
        val lore: List<String>,
    )

    private val sellTitle = "&8System Sell Menu"

    private val progressBarFilledPrefix = "&2"
    private val progressBarEmptyPrefix = "&7"
    private val progressBarCompleteChar = "#"
    private val progressBarIncompleteChar = "-"
    private val progressBarMaxBars = 16

    private val backButton = ButtonSpec(
        slot = 49,
        material = Material.BARRIER,
        name = "&cReturn",
        lore = listOf("&7Back to the sell bay"),
    )

    private val progressItems = mapOf(
        "WORKING" to ProgressItemSpec(
            material = Material.ORANGE_STAINED_GLASS_PANE,
            name = "&6Active Tier",
            lore = listOf(
                "%progress-bar% &f%multiplier%x &7(%progress%%)",
                "&7%current-spent%/%needing-spent%",
            ),
        ),
        "INCOMPLETE" to ProgressItemSpec(
            material = Material.GRAY_STAINED_GLASS_PANE,
            name = "&7Locked Tier",
            lore = listOf(
                "%progress-bar% &f%multiplier%x &7(%progress%%)",
                "&7%current-spent%/%needing-spent%",
            ),
        ),
        "COMPLETE" to ProgressItemSpec(
            material = Material.GREEN_STAINED_GLASS_PANE,
            name = "&aCleared Tier",
            lore = listOf(
                "%progress-bar% &f%multiplier%x &7(%progress%%)",
                "&7%current-spent%/%needing-spent%",
            ),
        ),
    )

    private val categorySpecs = mapOf(
        "CROPS" to buildCategory(
            slot = 36,
            title = "&8Crops Progress",
            name = "&eCROPS",
            material = Material.HAY_BLOCK,
            summary = "crops and farming materials",
        ),
        "ORES" to buildCategory(
            slot = 37,
            title = "&8Ores Progress",
            name = "&bORES",
            material = Material.AMETHYST_CLUSTER,
            summary = "ores and mining materials",
        ),
        "MOBS" to buildCategory(
            slot = 38,
            title = "&8Mobs Progress",
            name = "&aMOBS",
            material = Material.SPIDER_EYE,
            summary = "mob drops and loot materials",
        ),
        "NATURAL" to buildCategory(
            slot = 39,
            title = "&8Natural Progress",
            name = "&aNATURAL",
            material = Material.OAK_SAPLING,
            summary = "natural materials",
        ),
        "ARMOR_AND_TOOLS" to buildCategory(
            slot = 40,
            title = "&8Armor & Tools Progress",
            name = "&aARMOR AND TOOLS",
            material = Material.ANVIL,
            summary = "armor and tools materials",
        ),
        "FISH" to buildCategory(
            slot = 41,
            title = "&8Fish Progress",
            name = "&bFISH",
            material = Material.COD_BUCKET,
            summary = "fish and fishing loot",
        ),
        "BOOK" to buildCategory(
            slot = 42,
            title = "&8Books Progress",
            name = "&bBOOK",
            material = Material.LECTERN,
            summary = "books and enchanted books",
        ),
        "POTIONS" to buildCategory(
            slot = 43,
            title = "&8Potions Progress",
            name = "&dPOTIONS",
            material = Material.DRAGON_BREATH,
            summary = "potions and brewing items",
        ),
        "BLOCKS" to buildCategory(
            slot = 44,
            title = "&8Blocks Progress",
            name = "&bBLOCKS",
            material = Material.STONE_BRICKS,
            summary = "blocks and placeables",
        ),
    )

    private val worthTitle = "&8Market Ledger &7(Page %page%/%total%)"
    private val worthSortButtonMaterial = Material.COMPASS
    private val worthSortButtonName = "&aSort"
    private val worthSortSelected = "&a> %option%"
    private val worthSortUnselected = "&7  %option%"

    private val worthRefreshButtonMaterial = Material.CLOCK
    private val worthRefreshButtonName = "&aRescan Prices"
    private val worthRefreshButtonLore = listOf("&7Click to refresh")

    private val worthFilterButtonMaterial = Material.HOPPER
    private val worthFilterButtonName = "&aFilter"
    private val worthFilterSelected = "&a> %option%"
    private val worthFilterUnselected = "&7  %option%"

    private val worthNavigationMaterial = Material.ARROW
    private val worthPreviousButtonName = "&aBack"
    private val worthNextButtonName = "&aForward"
    private val worthPreviousButtonLore = listOf("&7Previous page")
    private val worthNextButtonLore = listOf("&7Next page")

    private val worthItemNameColor = "&a"
    private val worthItemLore = listOf("&7Price: &a$%price%")

    fun getSellGUITitle(): String = sellTitle

    fun getSellGUISize(): Int = 45

    fun getCategoryIconsStartSlot(): Int = 36

    fun areCategoryIconsEnabled(): Boolean = true

    fun getProgressGUITitle(categoryKey: String): String =
        categorySpecs[categoryKey]?.progressTitle ?: "$categoryKey Progress"

    fun getCategorySlot(categoryKey: String): Int =
        categorySpecs[categoryKey]?.slot ?: 36

    fun getBackButtonSlot(): Int = backButton.slot

    fun getBackButtonMaterial(): Material = backButton.material

    fun getBackButtonName(): String = backButton.name

    fun getBackButtonLore(): List<String> = backButton.lore

    fun getCategoryIconMaterial(categoryKey: String): Material =
        categorySpecs[categoryKey]?.categoryIcon?.material ?: Material.STONE

    fun getCategoryIconName(categoryKey: String): String =
        categorySpecs[categoryKey]?.categoryIcon?.name ?: categoryKey

    fun getCategoryIconLore(categoryKey: String): List<String> =
        categorySpecs[categoryKey]?.categoryIcon?.lore ?: emptyList()

    fun getCategoryIconFlags(categoryKey: String): List<ItemFlag> =
        categorySpecs[categoryKey]?.categoryIcon?.flags ?: emptyList()

    fun getCategoryIconCustomModelData(categoryKey: String): Int =
        categorySpecs[categoryKey]?.categoryIcon?.customModelData ?: 0

    fun getCategoryIconEnchantments(categoryKey: String): List<String> =
        categorySpecs[categoryKey]?.categoryIcon?.enchantments ?: emptyList()

    fun getProgressIconMaterial(categoryKey: String): Material =
        categorySpecs[categoryKey]?.progressIcon?.material ?: Material.STONE

    fun getProgressIconName(categoryKey: String): String =
        categorySpecs[categoryKey]?.progressIcon?.name ?: categoryKey

    fun getProgressIconLore(categoryKey: String): List<String> =
        categorySpecs[categoryKey]?.progressIcon?.lore ?: emptyList()

    fun getProgressIconFlags(categoryKey: String): List<ItemFlag> =
        categorySpecs[categoryKey]?.progressIcon?.flags ?: emptyList()

    fun getProgressIconCustomModelData(categoryKey: String): Int =
        categorySpecs[categoryKey]?.progressIcon?.customModelData ?: 0

    fun getProgressIconEnchantments(categoryKey: String): List<String> =
        categorySpecs[categoryKey]?.progressIcon?.enchantments ?: emptyList()

    fun getProgressItemMaterial(state: String): Material =
        progressItems[state]?.material ?: Material.GRAY_STAINED_GLASS_PANE

    fun getProgressItemName(state: String): String =
        progressItems[state]?.name ?: state

    fun getProgressItemLore(state: String): List<String> =
        progressItems[state]?.lore ?: emptyList()

    fun getFilledPrefix(): String = progressBarFilledPrefix

    fun getEmptyPrefix(): String = progressBarEmptyPrefix

    fun getCompleteChar(): String = progressBarCompleteChar

    fun getIncompleteChar(): String = progressBarIncompleteChar

    fun getMaxBars(): Int = progressBarMaxBars

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

    fun getWorthGUITitle(): String = worthTitle

    fun getWorthSortButtonMaterial(): Material = worthSortButtonMaterial

    fun getWorthSortButtonName(): String = worthSortButtonName

    fun getWorthLoreSelected(): String = worthSortSelected

    fun getWorthLoreUnselected(): String = worthSortUnselected

    fun getWorthRefreshButtonMaterial(): Material = worthRefreshButtonMaterial

    fun getWorthRefreshButtonName(): String = worthRefreshButtonName

    fun getWorthRefreshButtonLore(): List<String> = worthRefreshButtonLore

    fun getWorthFilterButtonMaterial(): Material = worthFilterButtonMaterial

    fun getWorthFilterButtonName(): String = worthFilterButtonName

    fun getWorthFilterLoreSelected(): String = worthFilterSelected

    fun getWorthFilterLoreUnselected(): String = worthFilterUnselected

    fun getWorthNavigationMaterial(): Material = worthNavigationMaterial

    fun getWorthPreviousButtonName(): String = worthPreviousButtonName

    fun getWorthPreviousButtonLore(): List<String> = worthPreviousButtonLore

    fun getWorthNextButtonName(): String = worthNextButtonName

    fun getWorthNextButtonLore(): List<String> = worthNextButtonLore

    fun getWorthItemNameColor(): String = worthItemNameColor

    fun getWorthItemLore(): List<String> = worthItemLore

    fun reload() {
        // Hardcoded GUI. No reload needed.
    }

    private fun buildCategory(
        slot: Int,
        title: String,
        name: String,
        material: Material,
        summary: String,
    ): CategorySpec {
        val flags = listOf(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        val enchants = listOf("unbreaking:1")
        val progressIcon = IconSpec(
            material = material,
            name = name,
            lore = listOf(
                "&7Sell $summary",
                "&7to upgrade your multiplier.",
                "",
                "&fProgress below",
            ),
            flags = flags,
            enchantments = enchants,
        )
        val categoryIcon = IconSpec(
            material = material,
            name = name,
            lore = listOf(
                "&7Sell $summary",
                "&7to upgrade your multiplier.",
                "",
                "&fNext: &a%multiplier%x",
                "%progress-bar% &7%progress%",
            ),
            flags = flags,
            enchantments = enchants,
        )
        return CategorySpec(
            slot = slot,
            progressTitle = title,
            categoryIcon = categoryIcon,
            progressIcon = progressIcon,
        )
    }
}
