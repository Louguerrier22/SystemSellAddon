package ca.l1cked.dsellsystem.api.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Summary-only payout event for plugins that only need totals.
 *
 * IMPORTANT: [totalPaid] already includes DSellSystem's personal multiplier.
 * External systems should apply any bonuses after this event.
 */
class DSellPayoutSummaryEvent(
    val player: Player,
    val personalMultiplier: Double,
    val totalPaid: Double,
    val transactionId: Long?,
    val timestampMillis: Long,
) : Event() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()

        fun hasListeners(): Boolean = handlerList.registeredListeners.isNotEmpty()
    }
}
