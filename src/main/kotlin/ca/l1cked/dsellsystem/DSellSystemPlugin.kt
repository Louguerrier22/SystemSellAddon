package ca.l1cked.dsellsystem

import ca.l1cked.dsellsystem.command.CommandRegistrar
import ca.l1cked.dsellsystem.bridge.MayorCallbackBridge
import ca.l1cked.dsellsystem.data.PlayerDataManagerDB
import ca.l1cked.dsellsystem.database.DatabaseManager
import ca.l1cked.dsellsystem.gui.ProgressGUI
import ca.l1cked.dsellsystem.gui.SellGUI
import ca.l1cked.dsellsystem.gui.WorthGUI
import ca.l1cked.dsellsystem.gui.SettingsGUI
import ca.l1cked.dsellsystem.gui.HistoryGUI
import ca.l1cked.dsellsystem.managers.GUIManager
import ca.l1cked.dsellsystem.managers.PricesManager
import ca.l1cked.dsellsystem.managers.ProtocolWorthOverlay
import ca.l1cked.dsellsystem.storage.SellStorage
import ca.l1cked.dsellsystem.storage.SQLiteSellStorage
import ca.l1cked.dsellsystem.command.HistoryService
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

class DSellSystemPlugin : JavaPlugin(), DSellSystemApi {

    companion object {
        lateinit var instance: DSellSystemPlugin
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
    lateinit var worthOverlay: ProtocolWorthOverlay
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
            logger.severe("Vault not found! Disabling plugin...")
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
        worthOverlay = ProtocolWorthOverlay(this)
        mayorBridge = MayorCallbackBridge(this)
        sellStorage = SQLiteSellStorage(dataFolder).also { it.init() }
        historyService = HistoryService(economy, sellStorage, config.getInt("history.page_size", 10), config.getInt("history.breakdown_max", 5))
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

        server.servicesManager.register(DSellSystemApi::class.java, this, this, ServicePriority.Normal)


        logger.info("DSellSystem has been enabled!")
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
            server.servicesManager.unregister(DSellSystemApi::class.java, this)
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
        logger.info("DSellSystem has been disabled!")
    }

    private fun setupEconomy(): Boolean {
        if (server.pluginManager.getPlugin("Vault") == null) {
            return false
        }
        val rsp = server.servicesManager.getRegistration(Economy::class.java) ?: return false
        economy = rsp.provider
        return true
    }

    override fun getPersonalMultiplier(player: org.bukkit.entity.Player): Double {
        return 1.0
    }

}
