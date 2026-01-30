package ca.l1cked.dsellsystem.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import java.util.regex.Pattern

object MessageUtil {
    private val hexPattern = Pattern.compile("#([A-Fa-f0-9]{6})")
    private val sectionSerializer = LegacyComponentSerializer.legacySection()

    fun deserialize(input: String?): Component {
        if (input == null) {
            return Component.empty()
        }
        val withHex = translateHexColors(input)
        val section = withHex.replace('&', '§')
        return sectionSerializer.deserialize(section)
    }

    fun serialize(component: Component): String = sectionSerializer.serialize(component)

    private fun translateHexColors(input: String): String {
        val matcher = hexPattern.matcher(input)
        val sb = StringBuffer(input.length + 32)
        while (matcher.find()) {
            val hex = matcher.group(1)
            val replacement = buildSectionHex(hex)
            matcher.appendReplacement(sb, replacement)
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun buildSectionHex(hex: String): String {
        val sb = StringBuilder("§x")
        for (c in hex.toCharArray()) {
            sb.append('§').append(c)
        }
        return sb.toString()
    }

    fun sendMessage(player: Player, message: String) {
        player.sendMessage(deserialize(message))
    }

    fun sendActionBar(player: Player, message: String) {
        player.sendActionBar(deserialize(message))
    }

    fun colorizeList(lines: List<String>): List<Component> = lines.map { deserialize(it) }

    fun legacy(input: String): String = serialize(deserialize(input))

    fun formatMoney(amount: Double): String {
        return when {
            amount >= 1.0e9 -> String.format("%.0fB", amount / 1.0e9)
            amount >= 1.0e6 -> String.format("%.0fM", amount / 1.0e6)
            amount >= 1.0e3 -> String.format("%.0fK", amount / 1.0e3)
            amount < 1.0 -> String.format("%.2f", amount)
            else -> String.format("%.0f", amount)
        }
    }
}
