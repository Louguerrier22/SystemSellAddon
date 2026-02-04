package ca.l1cked.systemselladdon.managers

import org.bukkit.entity.Player

/**
 * Abstraction for the (optional) worth-lore overlay.
 *
 * We keep this interface free of ProtocolLib types so systemselladdon can still load
 * even when ProtocolLib is not installed/enabled.
 */
interface WorthOverlay {
    fun isAvailable(): Boolean
    fun enable()
    fun disable()
    fun refreshPlayer(player: Player)
}

/**
 * Fallback overlay used when ProtocolLib is missing/incompatible.
 */
object NoopWorthOverlay : WorthOverlay {
    override fun isAvailable(): Boolean = false
    override fun enable() {}
    override fun disable() {}
    override fun refreshPlayer(player: Player) {}
}



