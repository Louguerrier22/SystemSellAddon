package ca.l1cked.systemselladdon.sell

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import java.util.Locale

data class PriceEntry(
    val categoryId: String,
    val categoryDisplay: String,
    val pricePerItem: Double,
)

class PricingService(private val categoriesSection: ConfigurationSection) {

    private val pricesByMaterial: Map<Material, PriceEntry> = loadPrices(categoriesSection)

    fun priceFor(material: Material): PriceEntry? = pricesByMaterial[material]

    fun categories(): Set<String> = pricesByMaterial.values.map { it.categoryId }.toSet()

    private fun loadPrices(root: ConfigurationSection): Map<Material, PriceEntry> {
        val out = mutableMapOf<Material, PriceEntry>()

        for (categoryId in root.getKeys(false)) {
            val catSec = root.getConfigurationSection(categoryId) ?: continue
            val display = catSec.getString("display") ?: categoryId
            val mats = catSec.getConfigurationSection("materials") ?: continue

            for (matKey in mats.getKeys(false)) {
                val price = mats.getDouble(matKey)
                val material = Material.matchMaterial(matKey.uppercase(Locale.ROOT)) ?: continue
                out[material] = PriceEntry(
                    categoryId = categoryId,
                    categoryDisplay = display,
                    pricePerItem = price,
                )
            }
        }

        return out
    }
}



