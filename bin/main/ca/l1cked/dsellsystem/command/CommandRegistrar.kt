package ca.l1cked.dsellsystem.command

import ca.l1cked.dsellsystem.DSellSystemPlugin
import ca.l1cked.dsellsystem.util.MessageUtil
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.incendo.cloud.component.CommandComponent
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.context.CommandInput
import org.incendo.cloud.paper.PaperCommandManager
import org.incendo.cloud.parser.standard.DoubleParser
import org.incendo.cloud.parser.standard.StringParser
import org.incendo.cloud.suggestion.BlockingSuggestionProvider

object CommandRegistrar {
    fun register(
        plugin: DSellSystemPlugin,
        manager: PaperCommandManager<CommandSourceStack>,
    ) {
        val historyService = plugin.historyService

        manager.command(
            manager.commandBuilder("sell")
                .handler { ctx: CommandContext<CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    if (sender !is Player) {
                        sender.sendMessage(MessageUtil.deserialize("&cThis command can only be used by players."))
                        return@handler
                    }
                    plugin.sellGUI.openGUI(sender)
                }
        )

        val historyPlayerArg = CommandComponent.builder<CommandSourceStack, String>("player", StringParser.stringParser())
            .required()
            .suggestionProvider(BlockingSuggestionProvider.Strings { _, input: CommandInput ->
                historyService.suggestions(input.lastRemainingToken())
            })
            .build()

        manager.command(
            manager.commandBuilder("sell")
                .literal("history")
                .handler { ctx: CommandContext<CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    if (sender !is Player) {
                        sender.sendMessage(MessageUtil.deserialize("&cThis command can only be used by players."))
                        return@handler
                    }
                    if (!plugin.config.getBoolean("history.enabled", false)) {
                        sender.sendMessage(MessageUtil.deserialize("&cHistory saving is disabled."))
                        return@handler
                    }
                    plugin.historyGUI.open(sender, sender.uniqueId, sender.name)
                }
        )

        manager.command(
            manager.commandBuilder("sell")
                .literal("history")
                .argument(historyPlayerArg)
                .permission("dsellsystem.history.other")
                .handler { ctx: CommandContext<CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    if (!sender.hasPermission("dsellsystem.history.other")) {
                        sender.sendMessage(MessageUtil.deserialize("&cYou don't have permission to use this command."))
                        return@handler
                    }
                    if (!plugin.config.getBoolean("history.enabled", false)) {
                        sender.sendMessage(MessageUtil.deserialize("&cHistory saving is disabled."))
                        return@handler
                    }
                    val name = ctx.get("player") as String
                    val uuid = plugin.sellStorage.resolveUuidByLastKnownName(name)
                    if (uuid == null) {
                        sender.sendMessage(MessageUtil.deserialize("&7No history found for &f$name&7."))
                        return@handler
                    }
                    if (sender !is Player) {
                        sender.sendMessage(MessageUtil.deserialize("&cThis command can only be used by players."))
                        return@handler
                    }
                    plugin.historyGUI.open(sender, uuid, name)
                }
        )

        manager.command(
            manager.commandBuilder("worth")
                .handler { ctx: CommandContext<CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    if (sender !is Player) {
                        sender.sendMessage(MessageUtil.deserialize("&cThis command can only be used by players."))
                        return@handler
                    }
                    plugin.worthGUI.openGUI(sender, 1)
                }
        )

        val worthItemArg = CommandComponent.builder<CommandSourceStack, String>("item", StringParser.stringParser())
            .required()
            .suggestionProvider(materialSuggestions())
            .build()

        manager.command(
            manager.commandBuilder("worth")
                .argument(worthItemArg)
                .handler { ctx: CommandContext<CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    if (sender !is Player) {
                        sender.sendMessage(MessageUtil.deserialize("&cThis command can only be used by players."))
                        return@handler
                    }
                    val raw = ctx.get("item") as String
                    val material = parseMaterial(raw)
                    if (material == null) {
                        sender.sendMessage(MessageUtil.deserialize("&cUnknown item: &f$raw"))
                        return@handler
                    }
                    val price = plugin.pricesManager.getPrice(material)
                    val category = plugin.pricesManager.getCategory(material)
                    if (price <= 0.0 && category == null) {
                        sender.sendMessage(MessageUtil.deserialize("&cNo price set for &f${material.name}"))
                        return@handler
                    }
                    val catName = category?.displayName ?: "&7Unknown"
                    sender.sendMessage(MessageUtil.deserialize("&a${material.name} &7-> &e$price &7($catName&7)"))
                }
        )

        val setWorthItemArg = CommandComponent.builder<CommandSourceStack, String>("item", StringParser.stringParser())
            .required()
            .suggestionProvider(materialSuggestions())
            .build()
        val setWorthPriceArg = CommandComponent.builder<CommandSourceStack, Double>("price", DoubleParser.doubleParser())
            .required()
            .build()

        manager.command(
            manager.commandBuilder("setworth")
                .permission("dsellsystem.setworth")
                .argument(setWorthItemArg)
                .argument(setWorthPriceArg)
                .handler { ctx: CommandContext<CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    if (!sender.hasPermission("dsellsystem.setworth")) {
                        sender.sendMessage(MessageUtil.deserialize("&cYou don't have permission to use this command."))
                        return@handler
                    }
                    val raw = ctx.get("item") as String
                    val material = parseMaterial(raw)
                    if (material == null) {
                        sender.sendMessage(MessageUtil.deserialize("&cUnknown item: &f$raw"))
                        return@handler
                    }
                    val price = (ctx.get("price") as Double).coerceAtLeast(0.0)
                    val ok = plugin.pricesManager.setPrice(material, price)
                    if (!ok) {
                        sender.sendMessage(MessageUtil.deserialize("&cItem not found in prices.yml category list."))
                        return@handler
                    }
                    sender.sendMessage(MessageUtil.deserialize("&aSet &f${material.name}&a price to &e$price"))
                }
        )

        manager.command(
            manager.commandBuilder("dsellsystem")
                .literal("settings")
                .permission("dsellsystem.settings")
                .handler { ctx: CommandContext<CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    if (sender !is Player) {
                        sender.sendMessage(MessageUtil.deserialize("&cThis command can only be used by players."))
                        return@handler
                    }
                    if (!sender.hasPermission("dsellsystem.settings")) {
                        sender.sendMessage(MessageUtil.deserialize("&cYou don't have permission to use this command."))
                        return@handler
                    }
                    plugin.settingsGUI.open(sender)
                }
        )

        manager.command(
            manager.commandBuilder("dsellsystem")
                .handler { ctx: CommandContext<CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    val version = plugin.pluginMeta.version
                    sender.sendMessage(MessageUtil.deserialize("&7DSellSystem v$version"))
                    sender.sendMessage(MessageUtil.deserialize("&7/dsellsystem reload - Reload configuration"))
                }
        )

        manager.command(
            manager.commandBuilder("dsellsystem")
                .literal("reload")
                .permission("dsellsystem.reload")
                .handler { ctx: CommandContext<CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    if (!sender.hasPermission("dsellsystem.reload")) {
                        sender.sendMessage(MessageUtil.deserialize("&cYou don't have permission to use this command."))
                        return@handler
                    }
                    plugin.reloadConfig()
                    plugin.guiManager.reload()
                    plugin.pricesManager.reload()
                    for (player in Bukkit.getOnlinePlayers()) {
                        plugin.worthOverlay.refreshPlayer(player)
                    }
                    sender.sendMessage(MessageUtil.deserialize("&aDSellSystem configuration reloaded!"))
                }
        )
    }

    private fun materialSuggestions(): BlockingSuggestionProvider.Strings<CommandSourceStack> {
        return BlockingSuggestionProvider.Strings { _, input: CommandInput ->
            val token = input.lastRemainingToken().uppercase()
            Material.values()
                .map { it.name }
                .filter { it.startsWith(token) }
                .take(50)
        }
    }

    private fun parseMaterial(input: String): Material? = Material.matchMaterial(input)
}
