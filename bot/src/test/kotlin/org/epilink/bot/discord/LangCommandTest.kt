/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.DatabaseFeatures.clearLanguagePreference
import org.epilink.bot.KoinBaseTest
import org.epilink.bot.discord.cmd.LangCommand
import org.epilink.bot.mockDatabase
import org.epilink.bot.mockHere
import org.epilink.bot.web.declareNoOpI18n
import org.koin.dsl.module
import kotlin.test.Test

class LangCommandTest : KoinBaseTest<Command>(
    Command::class,
    module {
        single<Command> { LangCommand() }
    }
) {
    @Test
    fun `Test lang command help`() {
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        mockHere<LinkDiscordMessages> { every { getLangHelpEmbed(any()) } returns embed }
        val dcf = mockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        test {
            run("e!lang", "", null, "", "1234", "")
            dcf.sendChannelMessage("1234", embed)
        }
    }

    @Test
    fun `Test lang command clear`() {
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        val db = mockDatabase(clearLanguagePreference("iid"))
        mockHere<LinkDiscordMessages> { every { getSuccessCommandReply(any(), "lang.clearSuccess") } returns embed }
        val dcf = mockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        test {
            run("e!lang clear", "clear", null, "iid", "1234", "")
        }
        coVerify {
            db.clearLanguagePreference("iid")
            dcf.sendChannelMessage("1234", embed)
        }
    }

    @Test
    fun `Test lang command set success`() {
        val embed = mockk<DiscordEmbed>()
        val i18n = mockHere<LinkDiscordMessagesI18n> {
            coEvery { getLanguage(any()) } returns ""
            coEvery { setLanguage("iid", "lll") } returns true
        }
        mockHere<LinkDiscordMessages> { every { getSuccessCommandReply(any(), "lang.success") } returns embed }
        val dcf = mockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        test {
            run("e!lang lll", "lll", null, "iid", "1234", "")
        }
        coVerify {
            i18n.setLanguage("iid", "lll")
            dcf.sendChannelMessage("1234", embed)
        }
    }

    @Test
    fun `Test lang command set fail`() {
        val embed = mockk<DiscordEmbed>()
        val i18n = mockHere<LinkDiscordMessagesI18n> {
            coEvery { getLanguage(any()) } returns ""
            coEvery { setLanguage("iid", "lll") } returns false
        }
        mockHere<LinkDiscordMessages> {
            every {
                getErrorCommandReply(
                    any(),
                    "lang.invalidLanguage",
                    "lll"
                )
            } returns embed
        }
        val dcf = mockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        test {
            run("e!lang lll", "lll", null, "iid", "1234", "")
        }
        coVerify {
            i18n.setLanguage("iid", "lll")
            dcf.sendChannelMessage("1234", embed)
        }
    }
}