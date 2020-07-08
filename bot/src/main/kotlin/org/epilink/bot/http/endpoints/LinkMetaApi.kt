/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http.endpoints

import guru.zoroark.ratelimit.rateLimited
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import org.epilink.bot.LegalText
import org.epilink.bot.LinkLegalTexts
import org.epilink.bot.LinkServerEnvironment
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.http.ApiEndpoint
import org.epilink.bot.http.ApiSuccessResponse
import org.epilink.bot.http.LinkDiscordBackEnd
import org.epilink.bot.http.LinkMicrosoftBackEnd
import org.epilink.bot.http.data.InstanceInformation
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.Duration

/**
 * Interface for the /meta API route
 */
interface LinkMetaApi {
    /**
     * Installs the route here (the route for "/api/v1" is automatically added, so you must call this at the
     * root route)
     */
    fun install(route: Route)
}

internal class LinkMetaApiImpl : LinkMetaApi, KoinComponent {
    /**
     * The environment the back end lives in
     */
    private val env: LinkServerEnvironment by inject()

    private val legal: LinkLegalTexts by inject()

    private val discordBackEnd: LinkDiscordBackEnd by inject()

    private val microsoftBackEnd: LinkMicrosoftBackEnd by inject()

    private val wsCfg: LinkWebServerConfiguration by inject()

    override fun install(route: Route) =
        with(route) { meta() }

    private fun Route.meta() {
        route("/api/v1/meta") {
            rateLimited(limit = 50, timeBeforeReset = Duration.ofMinutes(1)) {
                @ApiEndpoint("GET /api/v1/meta/info")
                get("info") {
                    call.respond(ApiSuccessResponse(data = getInstanceInformation()))
                }

                @ApiEndpoint("GET /api/v1/meta/tos")
                get("tos") {
                    call.respond(legal.termsOfServices.asResponseContent())
                }

                @ApiEndpoint("GET /api/v1/meta/privacy")
                get("privacy") {
                    call.respond(legal.privacyPolicy.asResponseContent())
                }
            }
        }
    }

    /**
     * Create an [InstanceInformation] object based on this back end's environment and configuration
     */
    private fun getInstanceInformation(): InstanceInformation =
        InstanceInformation(
            title = env.name,
            logo = wsCfg.logo,
            authorizeStub_msft = microsoftBackEnd.getAuthorizeStub(),
            authorizeStub_discord = discordBackEnd.getAuthorizeStub(),
            idPrompt = legal.idPrompt,
            footerUrls = wsCfg.footers,
            contacts = wsCfg.contacts
        )
}

private fun LegalText.asResponseContent(): OutgoingContent.ByteArrayContent =
    when(this) {
        is LegalText.Pdf -> ByteArrayContent(data, ContentType.Application.Pdf, OK)
        is LegalText.Html -> TextContent(text, ContentType.Text.Html, OK)
    }