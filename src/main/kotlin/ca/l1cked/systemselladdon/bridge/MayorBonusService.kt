package ca.l1cked.systemselladdon.bridge

import ca.l1cked.systemselladdon.SystemSellAddonPlugin
import ca.l1cked.systemselladdon.category.Category
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class MayorBonusService(private val plugin: SystemSellAddonPlugin) {
    private val mm = MiniMessage.miniMessage()
    private val apiProvider = AtomicReference<Any?>()
    private val lastTxByPlayer = ConcurrentHashMap<UUID, String>()

    @Volatile private var apiClass: Class<*>? = null
    @Volatile private var activePerksMethod: Method? = null
    @Volatile private var messagesGetter: Method? = null
    @Volatile private var messagesGet: Method? = null
    @Volatile private var configGetter: Method? = null
    @Volatile private var mayorPluginRef: Plugin? = null

    @Volatile private var cachedSellBonusTemplate: String? = null
    @Volatile private var cachedSettings: MayorSellSettings? = null
    @Volatile private var cacheLoaded: Boolean = false

    fun refreshProvider() {
        val mayorPlugin = plugin.server.pluginManager.getPlugin("MayorSystem")
        if (mayorPlugin == null || !mayorPlugin.isEnabled) {
            mayorPluginRef = null
            apiProvider.set(null)
            clearCache()
            return
        }
        mayorPluginRef = mayorPlugin
        val apiCls = loadMayorApiClass(mayorPlugin) ?: run {
            apiProvider.set(null)
            clearCache()
            return
        }
        @Suppress("UNCHECKED_CAST")
        val reg = plugin.server.servicesManager.getRegistration(apiCls as Class<Any>)
        val api = reg?.provider
        apiProvider.set(api)
        refreshCache(api)
    }

    fun refreshCache() {
        refreshCache(apiProvider.get())
    }

    private fun refreshCache(api: Any?) {
        if (api == null) {
            clearCache()
            return
        }
        cachedSettings = readMayorSellSettings()
        cachedSellBonusTemplate = readMayorMessageTemplate("public.sell_bonus")
        cacheLoaded = true
    }

    private fun clearCache() {
        cachedSettings = null
        cachedSellBonusTemplate = null
        cacheLoaded = false
    }

    fun applyMayorBonus(
        player: Player,
        paidByCategory: DoubleArray,
        totalPaid: Double,
        transactionId: String?
    ) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                applyMayorBonus(player, paidByCategory, totalPaid, transactionId)
            })
            return
        }
        if (totalPaid <= 0.0) return
        val api = apiProvider.get() ?: run {
            refreshProvider()
            apiProvider.get()
        } ?: return

        if (!isNewTransaction(player.uniqueId, transactionId)) return

        val perkIds = activePerks(api)
        if (perkIds.isEmpty()) return

        val settings = cachedSettings ?: readMayorSellSettings().also { cachedSettings = it }
        if (!settings.enabled) return
        val entries = buildBonusEntries(api, perkIds, paidByCategory, totalPaid, settings.stackMode, settings.allBonusStack)
        if (entries.isEmpty()) return

        val bonus = entries.sumOf { it.bonus }
        if (bonus <= 0.0) return

        val response = plugin.economy.depositPlayer(player, bonus)
        if (!response.transactionSuccess()) return

        for (entry in entries) {
            sendMayorBonusMessage(player, entry)
        }
    }

    private fun isNewTransaction(playerId: UUID, txId: String?): Boolean {
        if (txId != null) {
            val last = lastTxByPlayer[playerId]
            if (txId == last) return false
            lastTxByPlayer[playerId] = txId
            return true
        }
        return true
    }

    private fun loadMayorApiClass(mayorPlugin: org.bukkit.plugin.Plugin): Class<*>? {
        val cached = apiClass
        if (cached != null) return cached
        return runCatching {
            val cls = mayorPlugin.javaClass.classLoader.loadClass("mayorSystem.api.MayorSystemApi")
            apiClass = cls
            cls
        }.getOrNull()
    }

    private fun activePerks(api: Any): Set<String> {
        val method = activePerksMethod ?: runCatching {
            api.javaClass.getMethod("activePerkIds")
        }.getOrNull()?.also { activePerksMethod = it } ?: return emptySet()
        val res = runCatching { method.invoke(api) }.getOrNull()
        @Suppress("UNCHECKED_CAST")
        return (res as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
    }

    private data class MayorSellSettings(
        val enabled: Boolean,
        val stackMode: String,
        val allBonusStack: Boolean
    )

    private fun readMayorSellSettings(): MayorSellSettings {
        val cfg = mayorConfig()
        val enabled = cfg?.getBoolean("sell_bonus.enabled", true) ?: true
        val stackMode = cfg?.getString("sell_bonus.stack_mode", "MAX") ?: "MAX"
        val allStack = cfg?.getBoolean("sell_bonus.all_bonus_stack", true) ?: true
        return MayorSellSettings(enabled = enabled, stackMode = stackMode, allBonusStack = allStack)
    }

    private fun readMayorMessageTemplate(key: String): String? {
        val messages = mayorMessages() ?: return null
        val getMethod = messagesGet ?: runCatching {
            messages.javaClass.methods.firstOrNull { m ->
                m.name == "get" && m.parameterCount == 2 &&
                    m.parameterTypes[0] == String::class.java &&
                    Map::class.java.isAssignableFrom(m.parameterTypes[1])
            }
        }.getOrNull()?.also { messagesGet = it } ?: return null
        return runCatching { getMethod.invoke(messages, key, emptyMap<String, String>()) as? String }.getOrNull()
    }

    private fun mayorMessages(): Any? {
        val mayorPlugin = mayorPluginRef ?: return null
        val getter = messagesGetter ?: runCatching {
            mayorPlugin.javaClass.getMethod("getMessages")
        }.getOrNull()?.also { messagesGetter = it } ?: return null
        return runCatching { getter.invoke(mayorPlugin) }.getOrNull()
    }

    private fun mayorConfig(): FileConfiguration? {
        val mayorPlugin = mayorPluginRef ?: return null
        val getter = configGetter ?: runCatching {
            mayorPlugin.javaClass.getMethod("getConfig")
        }.getOrNull()?.also { configGetter = it } ?: return null
        return runCatching { getter.invoke(mayorPlugin) as? FileConfiguration }.getOrNull()
    }

    private data class BonusEntry(
        val name: String,
        val bonus: Double,
        val multiplier: Double
    )

    private data class MayorPerkOverride(
        val multiplier: Double,
        val appliesTo: String?
    )

    private fun mayorPerkOverride(perkId: String): MayorPerkOverride? {
        val sec = plugin.config.getConfigurationSection("mayor-perks.$perkId") ?: return null
        if (!sec.contains("sell_multiplier")) return null
        val multiplier = sec.getDouble("sell_multiplier")
        if (!multiplier.isFinite()) return null
        val appliesTo = sec.getString("applies_to")
        return MayorPerkOverride(multiplier = multiplier, appliesTo = appliesTo)
    }

    private fun buildBonusEntries(
        api: Any,
        perkIds: Set<String>,
        paidByCategory: DoubleArray,
        totalPaid: Double,
        stackMode: String,
        allBonusStack: Boolean
    ): List<BonusEntry> {
        val byCategory = mutableMapOf<Int, MutableList<Double>>()
        val global = mutableListOf<Double>()

        for (perkId in perkIds) {
            val override = mayorPerkOverride(perkId) ?: continue
            val multiplier = override.multiplier
            if (!multiplier.isFinite() || multiplier <= 1.0) continue
            val appliesRaw = override.appliesTo?.uppercase(Locale.US)
            if (appliesRaw.isNullOrBlank() || appliesRaw == "ALL") {
                global += multiplier
            } else {
                val id = appliesRaw.toIntOrNull() ?: continue
                if (id in 0 until Category.size) {
                    byCategory.getOrPut(id) { mutableListOf() } += multiplier
                }
            }
        }

        fun stack(list: List<Double>): Double {
            if (list.isEmpty()) return 1.0
            return if (stackMode.equals("PRODUCT", true)) {
                list.fold(1.0) { acc, m -> acc * m }
            } else {
                list.maxOrNull() ?: 1.0
            }
        }

        val out = mutableListOf<BonusEntry>()
        var paidWithCategoryBonus = 0.0
        val limit = minOf(paidByCategory.size, Category.size)
        for (i in 0 until limit) {
            val amount = paidByCategory[i]
            if (amount <= 0.0) continue
            val m = stack(byCategory[i] ?: emptyList())
            if (m > 1.000001) {
                out += BonusEntry(categoryNameForId(i), amount * (m - 1.0), m)
                paidWithCategoryBonus += amount
            }
        }

        val allMult = stack(global)
        if (allMult > 1.000001 && totalPaid > 0.0) {
            val base = if (allBonusStack) {
                totalPaid
            } else {
                (totalPaid - paidWithCategoryBonus).coerceAtLeast(0.0)
            }
            if (base > 0.0) {
                out += BonusEntry("All", base * (allMult - 1.0), allMult)
            }
        }
        return out
    }

    private fun sendMayorBonusMessage(player: Player, entry: BonusEntry) {
        if (!cacheLoaded) {
            refreshCache(apiProvider.get())
        }
        val template = cachedSellBonusTemplate ?: return
        val msg = applyPlaceholders(
            template,
            mapOf(
                "bonus" to String.format(Locale.US, "%.2f", entry.bonus),
                "multiplier" to String.format(Locale.US, "%.2f", entry.multiplier),
                "name" to entry.name,
                "category" to entry.name,
                "categorie" to entry.name
            )
        )
        player.sendMessage(mm.deserialize(msg))
    }

    private fun applyPlaceholders(text: String, placeholders: Map<String, String>): String {
        var out = text
        placeholders.forEach { (k, v) ->
            out = out.replace("%$k%", v)
        }
        return out
    }

    private fun categoryNameForId(id: Int): String = when (id) {
        0 -> "Crops"
        1 -> "Ores"
        2 -> "Mobs"
        3 -> "Natural"
        4 -> "Armor & Tools"
        5 -> "Fish"
        6 -> "Books"
        7 -> "Potions"
        8 -> "Blocks"
        else -> "All"
    }
}
