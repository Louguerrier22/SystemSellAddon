package ca.l1cked.dsellsystem.api.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.Collections

/**
 * Fired after DSellSystem successfully paid a player for a /sell.
 *
 * IMPORTANT: The totals in [totalPaid] and the per-category payout arrays already include
 * DSellSystem's own personal multiplier logic. External systems should apply any bonuses
 * *after* this event. DSellSystem never applies external perk bonuses.
 */
class DSellPayoutEvent(
    val player: Player,
    /** Category ids that were sold in this payout (parallel to [paidByCategory]). */
    categoryIds: IntArray,
    /** Payout per category (parallel to [categoryIds]), personal multiplier already applied. */
    paidByCategory: DoubleArray,
    /** The player's personal multiplier that DSellSystem applied. */
    val personalMultiplier: Double,
    /** Total money paid (sum of [paidByCategory]). */
    val totalPaid: Double,
    /** Transaction id in DSellSystem's database (nullable if not recorded). */
    val transactionId: Long?,
    /** Epoch millis when the sell happened. */
    val timestampMillis: Long,
) : Event() {

    private val categoryIdsInternal: IntArray = categoryIds.copyOf()
    private val paidByCategoryInternal: DoubleArray = paidByCategory.copyOf()

    @Volatile
    private var paidByCategoryMap: Map<Int, Double>? = null

    fun categoryIds(): IntArray = categoryIdsInternal.copyOf()

    fun paidByCategory(): DoubleArray = paidByCategoryInternal.copyOf()

    /** Lazily builds an unmodifiable map of category id -> payout. */
    fun paidByCategoryMap(): Map<Int, Double> {
        val cached = paidByCategoryMap
        if (cached != null) return cached
        val map = LinkedHashMap<Int, Double>(categoryIdsInternal.size)
        for (i in categoryIdsInternal.indices) {
            map[categoryIdsInternal[i]] = paidByCategoryInternal[i]
        }
        val unmodifiable = Collections.unmodifiableMap(map)
        paidByCategoryMap = unmodifiable
        return unmodifiable
    }

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()

        fun hasListeners(): Boolean = handlerList.registeredListeners.isNotEmpty()
    }
}
