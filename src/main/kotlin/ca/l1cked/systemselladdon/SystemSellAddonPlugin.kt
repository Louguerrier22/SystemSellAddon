package ca.l1cked.systemselladdon

import ca.l1cked.systemselladdon.command.CommandRegistrar
import ca.l1cked.systemselladdon.bridge.MayorCallbackBridge
import ca.l1cked.systemselladdon.data.PlayerDataManagerDB
import ca.l1cked.systemselladdon.database.DatabaseManager
import ca.l1cked.systemselladdon.database.DatabaseType
import ca.l1cked.systemselladdon.gui.ProgressGUI
import ca.l1cked.systemselladdon.gui.SellGUI
import ca.l1cked.systemselladdon.gui.WorthGUI
import ca.l1cked.systemselladdon.gui.SettingsGUI
import ca.l1cked.systemselladdon.gui.HistoryGUI
import ca.l1cked.systemselladdon.gui.WorthResetGUI
import ca.l1cked.systemselladdon.managers.GUIManager
import ca.l1cked.systemselladdon.managers.PricesManager
import ca.l1cked.systemselladdon.managers.NoopWorthOverlay
import ca.l1cked.systemselladdon.managers.ProtocolWorthOverlay
import ca.l1cked.systemselladdon.managers.WorthOverlay
import ca.l1cked.systemselladdon.managers.WorthLoreManager
import ca.l1cked.systemselladdon.storage.SellStorage
import ca.l1cked.systemselladdon.storage.SQLiteSellStorage
import ca.l1cked.systemselladdon.storage.MySQLSellStorage
import ca.l1cked.systemselladdon.command.HistoryService
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.paper.PaperCommandManager

class SystemSellAddonPlugin : JavaPlugin(), SystemSellAddonApi {

    companion object {
        lateinit var instance: SystemSellAddonPlugin
            private set
    }

    lateinit var economy: Economy
        private set
    lateinit var databaseManager: DatabaseManager
        private set
    lateinit var playerDataManager: PlayerDataManagerDB
        private set
    lateinit var pricesManager: PricesManager
        private set
    lateinit var guiManager: GUIManager
        private set
    lateinit var sellGUI: SellGUI
        private set
    lateinit var progressGUI: ProgressGUI
        private set
    lateinit var worthGUI: WorthGUI
        private set
    lateinit var settingsGUI: SettingsGUI
        private set
    lateinit var historyGUI: HistoryGUI
        private set
    lateinit var worthOverlay: WorthOverlay
        private set
    lateinit var worthLoreManager: WorthLoreManager
        private set
    lateinit var worthResetGUI: WorthResetGUI
        private set
    lateinit var sellStorage: SellStorage
        private set
    lateinit var historyService: HistoryService
        private set
    lateinit var mayorBridge: MayorCallbackBridge
        private set
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var commandManager: PaperCommandManager<CommandSourceStack>

    override fun onEnable() {
        instance = this
        saveDefaultConfig()

        if (!setupEconomy()) {
            logger.severe("Economy setup failed. Disabling plugin...")
            server.pluginManager.disablePlugin(this)
            return
        }

        databaseManager = DatabaseManager(this).also { it.connect() }
        pricesManager = PricesManager(this)
        guiManager = GUIManager(this)
        playerDataManager = PlayerDataManagerDB(this)
        sellGUI = SellGUI(this)
        progressGUI = ProgressGUI(this)
        worthGUI = WorthGUI(this)
        settingsGUI = SettingsGUI(this)
        historyGUI = HistoryGUI(this)
        worthOverlay = if (server.pluginManager.getPlugin("ProtocolLib")?.isEnabled == true) {
            logger.info("ProtocolLib detected. Worth overlay is available.")
            ProtocolWorthOverlay(this)
        } else {
            if (config.getBoolean("settings.worth-lore.enabled", false)) {
                logger.warning("ProtocolLib not found or not enabled. Worth overlay is disabled. Install ProtocolLib to enable worth lore overlay.")
            }
            NoopWorthOverlay
        }
        worthLoreManager = WorthLoreManager(this)
        worthResetGUI = WorthResetGUI(this)
        mayorBridge = MayorCallbackBridge(this)
        sellStorage = if (databaseManager.type == DatabaseType.MYSQL) {
            MySQLSellStorage(config, dataFolder.absolutePath)
        } else {
            SQLiteSellStorage(dataFolder)
        }.also { it.init() }
        historyService = HistoryService(economy, sellStorage, config.getInt("history.page_size", 10), config.getInt("history.breakdown_max", 5))
        ioScope.launch { historyService.refreshKnownNames() }
        val retentionDays = config.getInt("history.retention_days", 14)
        if (retentionDays > 0) {
            val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24L * 60L * 60L * 1000L)
            ioScope.launch { sellStorage.purgeOlderThan(cutoff) }
        }
        mayorBridge.refreshProvider()
        worthOverlay.enable()

        server.pluginManager.registerEvents(sellGUI, this)
        server.pluginManager.registerEvents(progressGUI, this)
        server.pluginManager.registerEvents(worthGUI, this)
        server.pluginManager.registerEvents(settingsGUI, this)
        server.pluginManager.registerEvents(historyGUI, this)
        server.pluginManager.registerEvents(worthLoreManager, this)
        server.pluginManager.registerEvents(worthResetGUI, this)

        commandManager = PaperCommandManager.builder()
            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
            .buildOnEnable(this)
        CommandRegistrar.register(this, commandManager)

        server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun onPluginEnable(e: org.bukkit.event.server.PluginEnableEvent) {
                if (e.plugin.name.equals("MayorSystem", ignoreCase = true)) {
                    mayorBridge.refreshProvider()
                    worthOverlay.enable()
                }
            }

            @org.bukkit.event.EventHandler
            fun onPluginDisable(e: org.bukkit.event.server.PluginDisableEvent) {
                if (e.plugin.name.equals("MayorSystem", ignoreCase = true)) {
                    mayorBridge.refreshProvider()
                    worthOverlay.enable()
                }
            }
        }, this)

        server.servicesManager.register(SystemSellAddonApi::class.java, this, this, ServicePriority.Normal)

        logger.info("SystemSellAddon has been enabled!")
    }

    override fun onDisable() {
        ioScope.cancel()
        try {
            worthOverlay.disable()
        } catch (_: Throwable) {
            // ignore
        }
        try {
            playerDataManager.saveAllData()
        } catch (_: Throwable) {
            // ignore
        }
        try {
            server.servicesManager.unregister(SystemSellAddonApi::class.java, this)
        } catch (_: Throwable) {
            // ignore
        }
        try {
            databaseManager.disconnect()
        } catch (_: Throwable) {
            // ignore
        }
        try {
            sellStorage.close()
        } catch (_: Throwable) {
            // ignore
        }
        logger.info("SystemSellAddon has been disabled!")
    }

    private fun setupEconomy(): Boolean {
        val vault = server.pluginManager.getPlugin("Vault")
        if (vault == null) {
            logger.severe("Vault plugin not found. Install Vault and an economy provider.")
            return false
        }
        if (!vault.isEnabled) {
            logger.severe("Vault plugin is installed but not enabled yet.")
            return false
        }
        val rsp = server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            val providers = server.servicesManager.getRegistrations(Economy::class.java)
                .joinToString(", ") { "${it.provider.name} (${it.plugin.name})" }
            logger.severe("Vault is enabled but no economy provider is registered. Install/enable an economy plugin (e.g., EssentialsX Economy).")
            if (providers.isNotBlank()) {
                logger.severe("Economy providers detected (not registered?): $providers")
            }
            return false
        }
        economy = rsp.provider
        logger.info("Hooked economy provider: ${economy.name}")
        return true
    }

    override fun getPersonalMultiplier(player: org.bukkit.entity.Player): Double {
        return 1.0
    }

}




