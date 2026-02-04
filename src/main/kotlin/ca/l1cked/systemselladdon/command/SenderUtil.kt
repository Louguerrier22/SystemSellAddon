package ca.l1cked.systemselladdon.command

import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender

object SenderUtil {

    /**
     * Extracts the Bukkit [CommandSender] from Paper's [CommandSourceStack].
     *
     * Paper has changed this API across versions, so we use reflection to support both
     * `getSender()` and `sender()` style accessors.
     */
    fun bukkitSender(stack: CommandSourceStack): CommandSender {
        val clazz = stack.javaClass
        val method = clazz.methods.firstOrNull { it.parameterCount == 0 && (it.name == "getSender" || it.name == "sender") }
            ?: error("Unable to extract CommandSender from CommandSourceStack (${clazz.name})")
        val sender = method.invoke(stack)
        return sender as? CommandSender
            ?: error("CommandSourceStack sender was not a Bukkit CommandSender (${sender?.javaClass?.name})")
    }
}



