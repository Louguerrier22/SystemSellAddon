package ca.l1cked.systemselladdon.bridge

import ca.l1cked.systemselladdon.SystemSellAddonPlugin
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.lang.reflect.Constructor
import java.lang.reflect.Method

class MayorCallbackBridge(private val plugin: SystemSellAddonPlugin) {
    private val cachedProvider = AtomicReference<Any?>()
    @Volatile private var callbackClass: Class<*>? = null
    @Volatile private var snapshotClass: Class<*>? = null
    @Volatile private var snapshotCtor: Constructor<*>? = null
    @Volatile private var callbackMethod: Method? = null
    @Volatile private var missingLogged = false

    fun refreshProvider() {
        val mayorPlugin = plugin.server.pluginManager.getPlugin("MayorSystem")
        if (mayorPlugin == null || !mayorPlugin.isEnabled) {
            cachedProvider.set(null)
            return
        }
        val callbackCls = loadCallbackClass() ?: run {
            cachedProvider.set(null)
            return
        }
        @Suppress("UNCHECKED_CAST")
        val reg = plugin.server.servicesManager.getRegistration(callbackCls as Class<Any>)
        cachedProvider.set(reg?.provider)
    }

    fun isAvailable(): Boolean = cachedProvider.get() != null

    fun notifyPayout(
        playerId: UUID,
        paidByCategory: DoubleArray,
        totalPaid: Double,
        timestampMillis: Long,
        transactionId: String?,
    ) {
        if (totalPaid <= 0.0) return
        val provider = cachedProvider.get() ?: run {
            refreshProvider()
            cachedProvider.get()
        } ?: return

        val snapshot = buildSnapshot(playerId, paidByCategory, timestampMillis, transactionId) ?: return
        val method = callbackMethod ?: resolveCallbackMethod(provider, snapshot) ?: return
        try {
            method.invoke(provider, snapshot)
        } catch (t: Throwable) {
            plugin.logger.warning("Failed to invoke MayorSystem callback: ${t.message}")
        }
    }

    private fun loadCallbackClass(): Class<*>? {
        val cached = callbackClass
        if (cached != null) return cached
        return try {
            val cls = Class.forName(MAYOR_CALLBACK_CLASS)
            callbackClass = cls
            cls
        } catch (t: Throwable) {
            logMissing("MayorSystem callback class not found. Bonus callbacks disabled.")
            null
        }
    }

    private fun loadSnapshotClass(): Class<*>? {
        val cached = snapshotClass
        if (cached != null) return cached
        return try {
            val cls = Class.forName(MAYOR_SNAPSHOT_CLASS)
            snapshotClass = cls
            cls
        } catch (t: Throwable) {
            logMissing("MayorSystem snapshot class not found. Bonus callbacks disabled.")
            null
        }
    }

    private fun buildSnapshot(
        playerId: UUID,
        paidByCategory: DoubleArray,
        timestampMillis: Long,
        transactionId: String?,
    ): Any? {
        val ctor = snapshotCtor ?: resolveSnapshotCtor() ?: return null
        return try {
            ctor.newInstance(playerId, paidByCategory, timestampMillis, transactionId)
        } catch (t: Throwable) {
            plugin.logger.warning("Failed to build MayorSystem payout snapshot: ${t.message}")
            null
        }
    }

    private fun resolveSnapshotCtor(): Constructor<*>? {
        val cls = loadSnapshotClass() ?: return null
        val ctor = cls.declaredConstructors.firstOrNull { ctor ->
            val params = ctor.parameterTypes
            params.size == 4 &&
                params[0].isAssignableFrom(UUID::class.java) &&
                params[1].isAssignableFrom(DoubleArray::class.java) &&
                (params[2] == Long::class.javaPrimitiveType || params[2] == Long::class.javaObjectType) &&
                params[3].isAssignableFrom(String::class.java)
        }
        if (ctor == null) {
            logMissing("MayorSystem snapshot constructor signature not found. Bonus callbacks disabled.")
            return null
        }
        ctor.isAccessible = true
        snapshotCtor = ctor
        return ctor
    }

    private fun resolveCallbackMethod(provider: Any, snapshot: Any): Method? {
        val method = provider.javaClass.methods.firstOrNull { m ->
            m.name == "onSellPayout" &&
                m.parameterTypes.size == 1 &&
                m.parameterTypes[0].isAssignableFrom(snapshot.javaClass)
        }
        if (method == null) {
            logMissing("MayorSystem callback method not found. Bonus callbacks disabled.")
            return null
        }
        callbackMethod = method
        return method
    }

    private fun logMissing(message: String) {
        if (missingLogged) return
        missingLogged = true
        plugin.logger.warning(message)
    }

    private companion object {
        const val MAYOR_CALLBACK_CLASS = "mayorSystem.econ.MayorSellCallback"
        const val MAYOR_SNAPSHOT_CLASS = "mayorSystem.econ.DSellPayoutSnapshot"
    }
}



