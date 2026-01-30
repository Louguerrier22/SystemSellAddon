package ca.l1cked.dsellsystem.command

import ca.l1cked.dsellsystem.sell.SellOutcome
import ca.l1cked.dsellsystem.sell.SellService
import ca.l1cked.dsellsystem.util.Messages
import org.bukkit.entity.Player
import org.incendo.cloud.component.CommandComponent
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.context.CommandInput
import org.incendo.cloud.paper.PaperCommandManager
import org.incendo.cloud.parser.standard.StringParser
import org.incendo.cloud.suggestion.BlockingSuggestionProvider

object SellCommands {

    private const val ARG_PLAYER = "player"

    fun register(
        manager: PaperCommandManager<io.papermc.paper.command.brigadier.CommandSourceStack>,
        sellService: SellService,
        historyService: HistoryService,
        messages: Messages,
        showSellBreakdown: Boolean,
    ) {
        // /sell
        manager.command(
            manager.commandBuilder("sell")
                .handler { ctx: CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    if (sender !is Player) {
                        sender.sendMessage(messages.msg("messages.players-only"))
                        return@handler
                    }
                    val player = sender
                    when (val outcome = sellService.sellAll(player)) {
                        is SellOutcome.NoItems -> {
                            player.sendMessage(messages.msg("messages.no-items"))
                        }
                        is SellOutcome.EconomyFailure -> {
                            val err = outcome.errorMessage ?: "unknown"
                            if (err == "disabled") {
                                player.sendMessage(messages.msg("messages.disabled"))
                            } else {
                                player.sendMessage(messages.msg("messages.economy-failed", "error" to err))
                            }
                        }
                        is SellOutcome.Success -> {
                            val result = outcome.result
                            player.sendMessage(
                                messages.msg(
                                    "messages.sold",
                                    "amount" to messages.money(result.totalPaid),
                                    "multiplier" to messages.multiplier(result.personalMultiplier),
                                )
                            )

                            if (showSellBreakdown) {
                                player.sendMessage(messages.msg("messages.sold-breakdown-header"))
                                result.paidByCategory.entries
                                    .sortedByDescending { it.value }
                                    .forEach { (catId, amount) ->
                                        val display = result.categoryDisplays[catId] ?: catId
                                        player.sendMessage(
                                            messages.msg(
                                                "messages.sold-breakdown-line",
                                                "category" to display,
                                                "amount" to messages.money(amount),
                                            )
                                        )
                                    }
                            }
                        }
                    }
                }
        )

        // /sell history
        manager.command(
            manager.commandBuilder("sell")
                .literal("history")
                .handler { ctx: CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    if (sender !is Player) {
                        sender.sendMessage(messages.msg("messages.players-only"))
                        return@handler
                    }
                    val player = sender
                    val history = historyService.historyFor(player)
                    if (history.isEmpty()) {
                        player.sendMessage(messages.msg("messages.history-empty"))
                        return@handler
                    }

                    player.sendMessage(
                        messages.msg(
                            "messages.history-header",
                            "player" to player.name,
                            "count" to history.size.toString(),
                        )
                    )
                    history.forEach { tx ->
                        player.sendMessage(messages.colorize("&7- &f${historyService.formatLine(tx)}"))
                    }
                }
        )

        // /sell history <player> (admin)
        val playerArg = CommandComponent.builder<io.papermc.paper.command.brigadier.CommandSourceStack, String>(ARG_PLAYER, StringParser.stringParser())
            .required()
            .suggestionProvider(playerNameSuggestions(historyService))
            .build()

        manager.command(
            manager.commandBuilder("sell")
                .literal("history")
                .argument(playerArg)
                .permission("dsellsystem.history.other")
                .handler { ctx: CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ->
                    val sender = SenderUtil.bukkitSender(ctx.sender())
                    val name = ctx.get(ARG_PLAYER) as String
                    val history = historyService.historyForName(name)
                    if (history.isEmpty()) {
                        sender.sendMessage(messages.msg("messages.history-empty"))
                        return@handler
                    }

                    sender.sendMessage(
                        messages.msg(
                            "messages.history-header",
                            "player" to name,
                            "count" to history.size.toString(),
                        )
                    )
                    history.forEach { tx ->
                        sender.sendMessage(messages.colorize("&7- &f${historyService.formatLine(tx)}"))
                    }
                }
        )
    }

    private fun playerNameSuggestions(historyService: HistoryService): BlockingSuggestionProvider.Strings<io.papermc.paper.command.brigadier.CommandSourceStack> {
        return BlockingSuggestionProvider.Strings { _, input: CommandInput ->
            val token = input.lastRemainingToken()
            historyService.suggestions(token)
        }
    }
}
