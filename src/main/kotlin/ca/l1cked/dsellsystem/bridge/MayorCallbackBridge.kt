package ca.l1cked.dsellsystem.bridge

import ca.l1cked.dsellsystem.DSellSystemPlugin
import mayorSystem.econ.DSellPayoutSnapshot
import mayorSystem.econ.MayorSellCallback
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class MayorCallbackBridge(private val plugin: DSellSystemPlugin) {
    private val cachedProvider = AtomicReference<MayorSellCallback?>()

    fun refreshProvider() {
        val mayorPlugin = plugin.server.pluginManager.getPlugin("MayorSystem")
        if (mayorPlugin == null || !mayorPlugin.isEnabled) {
            cachedProvider.set(null)
            return
        }
        val reg = plugin.server.servicesManager.getRegistration(MayorSellCallback::class.java)
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

        val snapshot = DSellPayoutSnapshot(
            playerId = playerId,
            paidByCategory = paidByCategory,
            timestampMillis = timestampMillis,
            transactionId = transactionId,
        )
        provider.onSellPayout(snapshot)
    }
}
