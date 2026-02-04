package ca.l1cked.systemselladdon.util

import org.bukkit.inventory.ItemStack
import java.util.Base64

object ItemStackSerializer {
    fun encode(item: ItemStack): String {
        val bytes = item.serializeAsBytes()
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun decode(data: String): ItemStack? {
        return try {
            val bytes = Base64.getDecoder().decode(data)
            ItemStack.deserializeBytes(bytes)
        } catch (_: Throwable) {
            null
        }
    }
}



