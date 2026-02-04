package ca.l1cked.systemselladdon.storage

import java.util.UUID

data class SellTransaction(
    val id: Long,
    val playerUuid: UUID,
    val playerName: String,
    val timestampMillis: Long,
    val personalMultiplier: Double,
    val totalPaid: Double,
    val paidByCategory: Map<String, Double>,
)



