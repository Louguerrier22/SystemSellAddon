package ca.l1cked.systemselladdon.managers

import ca.l1cked.systemselladdon.SystemSellAddonPlugin
import ca.l1cked.systemselladdon.category.Category
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.nio.file.Files

class PricesManager(private val plugin: SystemSellAddonPlugin) {
    private lateinit var pricesFile: File
    private lateinit var pricesConfig: FileConfiguration
    @Volatile private var priceCache: Map<Material, Double> = emptyMap()
    @Volatile private var categoryCache: Map<Material, Category> = emptyMap()

    init {
        loadPrices()
    }

    private fun loadPrices() {
        pricesFile = File(plugin.dataFolder, "prices.yml")
        if (!pricesFile.exists()) {
            pricesFile.parentFile.mkdirs()
            try {
                plugin.getResource("prices.yml")?.use { input ->
                    Files.copy(input, pricesFile.toPath())
                } ?: pricesFile.createNewFile()
            } catch (e: IOException) {
                plugin.logger.severe("Could not create prices.yml!")
                e.printStackTrace()
            }
        }
        pricesConfig = YamlConfiguration.loadConfiguration(pricesFile)
        cachePrices(true)
    }

    private fun cachePrices(log: Boolean) {
        val newPrices: MutableMap<Material, Double> = HashMap()
        val newCategories: MutableMap<Material, Category> = HashMap()
        val typeSection = pricesConfig.getConfigurationSection("TYPE")
        if (typeSection == null) {
            if (log) {
                plugin.logger.warning("No TYPE section found in prices.yml!")
            }
            priceCache = emptyMap()
            categoryCache = emptyMap()
            return
        }
        for (category in Category.values()) {
            val catSection = typeSection.getConfigurationSection(category.configPath) ?: continue
            cacheCategory(catSection, category, newPrices, newCategories)
        }
        priceCache = newPrices
        categoryCache = newCategories
        if (log) {
            plugin.logger.info("Loaded ${priceCache.size} items from prices.yml")
        }
    }

    private fun cacheCategory(
        section: ConfigurationSection,
        category: Category,
        priceMap: MutableMap<Material, Double>,
        categoryMap: MutableMap<Material, Category>,
    ) {
        for (key in section.getKeys(false)) {
            try {
                val material = Material.valueOf(key)
                val price = section.getDouble(key, 0.0)
                priceMap[material] = price
                categoryMap[material] = category
            } catch (_: IllegalArgumentException) {
                // ignore invalid material
            }
        }
    }

    fun getPrice(material: Material): Double =
        priceCache[material] ?: 0.0

    fun getCategory(material: Material): Category? =
        categoryCache[material]

    fun getCategoryItems(category: Category): Map<Material, Double> {
        val out = HashMap<Material, Double>()
        for ((material, cat) in categoryCache) {
            if (cat == category) {
                out[material] = priceCache[material] ?: 0.0
            }
        }
        return out
    }

    fun reload() {
        pricesConfig = YamlConfiguration.loadConfiguration(pricesFile)
        cachePrices(true)
    }

    fun reloadPrices() {
        pricesConfig = YamlConfiguration.loadConfiguration(pricesFile)
        cachePrices(false)
    }

    fun getAllPrices(): Map<Material, Double> =
        HashMap(priceCache)

    fun setPrice(material: Material, price: Double): Boolean {
        val category = categoryCache[material] ?: return false
        val section = pricesConfig.getConfigurationSection("TYPE.${category.configPath}") ?: return false
        section.set(material.name, price)
        val updated = HashMap(priceCache)
        updated[material] = price
        priceCache = updated
        save()
        return true
    }

    fun save() {
        try {
            pricesConfig.save(pricesFile)
        } catch (e: IOException) {
            plugin.logger.severe("Could not save prices.yml!")
            e.printStackTrace()
        }
    }
}



