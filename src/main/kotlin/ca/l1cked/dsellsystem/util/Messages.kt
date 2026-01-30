package ca.l1cked.dsellsystem.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.milkbowl.vault.economy.Economy
import org.bukkit.configuration.file.FileConfiguration

class Messages(private val config: FileConfiguration, private val economy: Economy) {

    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    private val prefix: String = config.getString("messages.prefix") ?: ""

    fun colorize(input: String): Component = legacy.deserialize(input)

    /**
     * @param path Config path (ex: "messages.no-items").
     */
    fun msg(path: String, placeholders: Map<String, String> = emptyMap()): Component {
        val raw = config.getString(path) ?: ""
        var out = raw.replace("{prefix}", prefix)
        for ((k, v) in placeholders) {
            out = out.replace("{$k}", v)
        }
        return colorize(out)
    }

    fun msg(path: String, vararg placeholders: Pair<String, String>): Component {
        return msg(path, placeholders.toMap())
    }

    fun money(amount: Double): String = economy.format(amount)

    fun multiplier(value: Double): String {
        val str = String.format("%.2f", value)
        return str.trimEnd('0').trimEnd('.')
    }
}
