package org.epilink.bot.discord.cmd

import org.epilink.bot.db.LinkUser
import org.epilink.bot.discord.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(KoinApiExtension::class)
class CountCommand : Command, KoinComponent {
    private val msg: LinkDiscordMessages by inject()
    private val i18n: LinkDiscordMessagesI18n by inject()
    private val discord: LinkDiscordClientFacade by inject()
    private val resolver: LinkDiscordTargets by inject()

    override val name = "count"
    override val permissionLevel = PermissionLevel.Admin
    override val requireMonitoredServer = true

    override suspend fun run(
        fullCommand: String,
        commandBody: String,
        sender: LinkUser?,
        senderId: String,
        channelId: String,
        guildId: String?
    ) {
        requireNotNull(sender)
        requireNotNull(guildId)

        val result = resolver.parseDiscordTarget(commandBody)
            .takeIf { it is TargetParseResult.Success }
            ?.let { resolver.resolveDiscordTarget(it as TargetParseResult.Success, guildId) }
            ?.takeIf { it !is TargetResult.RoleNotFound }
        val embed = if (result == null) {
            msg.getWrongTargetCommandReply(i18n.getLanguage(senderId), commandBody)
        } else {
            when (result) {
                is TargetResult.Everyone ->
                    success(senderId, discord.getMembers(guildId).size)
                is TargetResult.Role ->
                    success(senderId, discord.getMembersWithRole(result.id, guildId).size)
                is TargetResult.User ->
                    if (discord.isUserInGuild(result.id, guildId)) success(senderId, 1)
                    else msg.getWrongTargetCommandReply(i18n.getLanguage(senderId), commandBody)
                is TargetResult.RoleNotFound ->
                    error("Invalid state: cannot get to TargetResult.RoleNotFound here")
            }
        }
        discord.sendChannelMessage(channelId, embed)
    }

    private suspend fun success(senderId: String, size: Int) : DiscordEmbed {
        return msg.getSuccessCommandReply(i18n.getLanguage(senderId), "count.success", size)
    }
}
