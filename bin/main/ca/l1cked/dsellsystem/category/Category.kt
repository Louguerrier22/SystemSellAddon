package ca.l1cked.dsellsystem.category

import org.bukkit.Material

enum class Category(
    val id: Int,
    val key: String,
    val displayName: String,
    val description1: String,
    val description2: String,
    val displayMaterial: Material,
    val configPath: String,
) {
    CROPS(
        0,
        "CROPS",
        "&eCROPS",
        "&7Sell crops and farming materials",
        "&7to upgrade your multiplier",
        Material.WHEAT,
        "CROPS",
    ),
    ORES(
        1,
        "ORES",
        "&bORES",
        "&7Sell ores and mining materials",
        "&7to upgrade your multiplier",
        Material.DIAMOND,
        "ORES",
    ),
    MOBS(
        2,
        "MOBS",
        "&aMOBS",
        "&7Sell mob drops and loot",
        "&7to upgrade your multiplier",
        Material.ROTTEN_FLESH,
        "MOBS",
    ),
    NATURAL(
        3,
        "NATURAL",
        "&aNATURAL",
        "&7Sell natural materials",
        "&7to upgrade your multiplier",
        Material.OAK_LOG,
        "NATURAL",
    ),
    ARMOR_AND_TOOLS(
        4,
        "ARMOR_AND_TOOLS",
        "&aARMOR AND TOOLS",
        "&7Sell armor and tools",
        "&7to upgrade your multiplier",
        Material.IRON_CHESTPLATE,
        "ARMOR_AND_TOOLS",
    ),
    FISH(
        5,
        "FISH",
        "&bFISH",
        "&7Sell fish and fishing loot",
        "&7to upgrade your multiplier",
        Material.COOKED_COD,
        "FISH",
    ),
    BOOK(
        6,
        "BOOK",
        "&bBOOK",
        "&7Sell books and enchanted books",
        "&7to upgrade your multiplier",
        Material.ENCHANTED_BOOK,
        "BOOK",
    ),
    POTIONS(
        7,
        "POTIONS",
        "&dPOTIONS",
        "&7Sell potions and brewing items",
        "&7to upgrade your multiplier",
        Material.POTION,
        "POTIONS",
    ),
    BLOCKS(
        8,
        "BLOCKS",
        "&bBLOCKS",
        "&7Sell blocks and placeables",
        "&7to upgrade your multiplier",
        Material.BRICKS,
        "BLOCKS",
    ),
    ;

    companion object {
        private val byId: Map<Int, Category> = values().associateBy { it.id }
        private val byKey: Map<String, Category> = values().associateBy { it.key }

        fun fromId(id: Int): Category? = byId[id]

        fun idForKey(key: String): Int? = byKey[key]?.id

        fun keyForId(id: Int): String? = byId[id]?.key

        fun displayForId(id: Int): String? = byId[id]?.displayName

        val size: Int = values().size
    }
}
