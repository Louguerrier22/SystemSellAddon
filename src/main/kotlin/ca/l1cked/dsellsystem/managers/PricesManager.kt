package ca.l1cked.dsellsystem.managers

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.category.Category
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.nio.file.Files

class PricesManager(private val plugin: DSellSystemPlugin) {
    private lateinit var pricesFile: File
    private lateinit var pricesConfig: FileConfiguration
    private val priceCache: MutableMap<Material, Double> = HashMap()
    private val categoryCache: MutableMap<Material, Category> = HashMap()

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
        priceCache.clear()
        categoryCache.clear()
        val typeSection = pricesConfig.getConfigurationSection("TYPE")
        if (typeSection == null) {
            if (log) {
                plugin.logger.warning("No TYPE section found in prices.yml!")
            }
            return
        }
        for (category in Category.values()) {
            val catSection = typeSection.getConfigurationSection(category.configPath) ?: continue
            cacheCategory(catSection, category)
        }
        if (log) {
            plugin.logger.info("Loaded ${priceCache.size} items from prices.yml")
        }
    }

    private fun cacheCategory(section: ConfigurationSection, category: Category) {
        for (key in section.getKeys(false)) {
            try {
                val material = Material.valueOf(key)
                val price = section.getDouble(key, 0.0)
                priceCache[material] = price
                categoryCache[material] = category
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
        priceCache[material] = price
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
