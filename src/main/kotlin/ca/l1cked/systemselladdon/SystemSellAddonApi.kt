package ca.l1cked.systemselladdon

import org.bukkit.entity.Player

/**
 * Lightweight API exposed through Bukkit's [org.bukkit.plugin.ServicesManager].
 *
 * Most integrations should listen to [ca.l1cked.systemselladdon.api.event.SystemSellAddonPayoutSummaryEvent]
 * or [ca.l1cked.systemselladdon.api.event.SystemSellAddonPayoutEvent].
 *
 * SystemSellAddon never applies external perk bonuses. External plugins should apply their
 * own bonuses after receiving the payout event. If you query this service often, cache
 * the provider from Bukkit's ServicesManager.
 */
interface SystemSellAddonApi {
    /** The personal multiplier SystemSellAddon applies before firing payout events. */
    fun getPersonalMultiplier(player: Player): Double
}



