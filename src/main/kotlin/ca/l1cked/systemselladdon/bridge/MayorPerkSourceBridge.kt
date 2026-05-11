package ca.l1cked.systemselladdon.bridge

import ca.l1cked.systemselladdon.SystemSellAddonPlugin
import mayorSystem.api.MayorAddonRegistration
import mayorSystem.api.MayorPerkDefinition
import mayorSystem.api.MayorPerkSection
import mayorSystem.api.MayorPerkSource
import mayorSystem.api.MayorSystemApi
import java.util.logging.Level

class MayorPerkSourceBridge(
    private val plugin: SystemSellAddonPlugin
) : MayorPerkSourceRegistration {
    private var registration: MayorAddonRegistration? = null

    override fun register() {
        if (registration != null) return

        val api = plugin.server.servicesManager.load(MayorSystemApi::class.java)
        if (api == null) {
            plugin.logger.fine("MayorSystem API service is not available; sell perk source was not registered.")
            return
        }

        registration = runCatching {
            api.registerPerkSource(plugin, SystemSellMayorPerkSource(plugin))
        }.onSuccess {
            plugin.logger.info("Registered SystemSellAddon perk source with MayorSystem.")
        }.onFailure { ex ->
            plugin.logger.log(Level.WARNING, "Failed to register SystemSellAddon perk source with MayorSystem.", ex)
        }.getOrNull()
    }

    override fun unregister() {
        registration?.close()
        registration = null
    }
}

private class SystemSellMayorPerkSource(
    private val plugin: SystemSellAddonPlugin
) : MayorPerkSource {
    override val id: String = "systemselladdon"
    override val displayName: String = "SystemSellAddon"
    override val available: Boolean = true

    override fun sections(): List<MayorPerkSection> {
        val perksSection = plugin.config.getConfigurationSection("mayor-perks") ?: return emptyList()
        val perks = perksSection.getKeys(false).mapNotNull { perkId ->
            if (!MAYOR_ID_PATTERN.matches(perkId)) {
                plugin.logger.warning("Skipping invalid MayorSystem sell perk id '$perkId'. Ids must match $MAYOR_ID_REGEX.")
                return@mapNotNull null
            }
            val path = "mayor-perks.$perkId"
            MayorPerkDefinition(
                id = perkId,
                enabled = plugin.config.getBoolean("$path.enabled", true),
                displayName = plugin.config.getString("$path.display_name") ?: "<white>$perkId</white>",
                icon = plugin.config.getString("$path.icon") ?: "GOLD_INGOT",
                lore = plugin.config.getStringList("$path.lore"),
                adminLore = plugin.config.getStringList("$path.admin_lore"),
                onStart = emptyList(),
                onEnd = emptyList()
            )
        }
        if (perks.isEmpty()) return emptyList()
        return listOf(
            MayorPerkSection(
                id = "economy",
                enabled = true,
                pickLimit = 0,
                displayName = "<gradient:#f7971e:#ffd200>Economy</gradient>",
                icon = "GOLD_INGOT",
                perks = perks
            )
        )
    }

    private companion object {
        private const val MAYOR_ID_REGEX = "^[a-z0-9][a-z0-9_.-]{0,63}$"
        private val MAYOR_ID_PATTERN = Regex(MAYOR_ID_REGEX)
    }
}
