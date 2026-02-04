package ca.l1cked.systemselladdon.sell

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player

class MultiplierService(private val section: ConfigurationSection) {
    private val defaultMultiplier: Double = section.getDouble("default", 1.0)
    private val permissionMultipliers: Map<String, Double> = loadPermissionMultipliers(section)

    fun getPersonalMultiplier(player: Player): Double {
        var best = defaultMultiplier
        for ((perm, mult) in permissionMultipliers) {
            if (player.hasPermission(perm) && mult > best) {
                best = mult
            }
        }
        return best
    }

    private fun loadPermissionMultipliers(section: ConfigurationSection): Map<String, Double> {
        val perms = section.getConfigurationSection("permission-multipliers") ?: return emptyMap()
        val out = mutableMapOf<String, Double>()
        for (k in perms.getKeys(false)) {
            out[k] = perms.getDouble(k)
        }
        return out
    }
}



