package ca.l1cked.systemselladdon.storage

import org.bukkit.inventory.ItemStack
import java.util.UUID

data class SellItemEntry(
    val id: Long,
    val transactionId: Long,
    val playerUuid: UUID,
    val timestampMillis: Long,
    val item: ItemStack,
)



