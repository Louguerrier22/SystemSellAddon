package ca.l1cked.systemselladdon.util

import org.bukkit.configuration.file.FileConfiguration
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object LevelPriceUtil {
    private const val MODE_LIST = "list"
    private const val MODE_FORMULA = "formula"

    fun getMaxLevels(config: FileConfiguration): Int {
        return if (getMode(config) == MODE_FORMULA) {
            config.getInt("level-prices.max-levels", 20).coerceAtLeast(1)
        } else {
            val list = getList(config)
            if (list.isNotEmpty()) list.size else 1
        }
    }

    fun getPriceForLevel(config: FileConfiguration, level: Int): Double {
        val safeLevel = max(0, level)
        return if (getMode(config) == MODE_FORMULA) {
            val base = config.getDouble("level-prices.formula.base", 25000.0)
            val growth = config.getDouble("level-prices.formula.growth", 1.6)
            val exponent = config.getDouble("level-prices.formula.exponent", 1.0)
            val addPerLevel = config.getDouble("level-prices.formula.add-per-level", 0.0)
            val minValue = config.getDouble("level-prices.formula.min", 0.0)
            val maxValue = config.getDouble("level-prices.formula.max", Double.MAX_VALUE)
            val raw = base * growth.pow(safeLevel.toDouble() * exponent) + (addPerLevel * safeLevel)
            raw.coerceIn(minValue, maxValue)
        } else {
            val list = getList(config)
            if (list.isNotEmpty()) {
                list[min(safeLevel, list.size - 1)]
            } else {
                500000.0
            }
        }
    }

    private fun getMode(config: FileConfiguration): String =
        config.getString("level-prices.mode", MODE_LIST)?.lowercase() ?: MODE_LIST

    private fun getList(config: FileConfiguration): List<Double> {
        val list = config.getDoubleList("level-prices.list")
        if (list.isNotEmpty()) return list
        return config.getDoubleList("level-prices")
    }
}
