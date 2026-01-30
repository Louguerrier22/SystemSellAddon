package ca.l1cked.dsellsystem.data

import ca.l1cked.dsellsystem.category.Category
import java.util.EnumMap
import java.util.UUID

class PlayerData(val uuid: UUID) {
    private val categoryProgress = EnumMap<Category, Double>(Category::class.java)
    private val categoryMultipliers = EnumMap<Category, Double>(Category::class.java)

    init {
        for (category in Category.values()) {
            categoryProgress[category] = 0.0
            categoryMultipliers[category] = 1.0
        }
    }

    fun getProgress(category: Category): Double = categoryProgress[category] ?: 0.0

    fun setProgress(category: Category, value: Double) {
        categoryProgress[category] = value
    }

    fun addProgress(category: Category, delta: Double) {
        val current = getProgress(category)
        categoryProgress[category] = current + delta
    }

    fun getMultiplier(category: Category): Double = categoryMultipliers[category] ?: 1.0

    fun setMultiplier(category: Category, value: Double) {
        categoryMultipliers[category] = value
    }

    fun getAllProgress(): Map<Category, Double> = EnumMap(categoryProgress)

    fun getAllMultipliers(): Map<Category, Double> = EnumMap(categoryMultipliers)
}
