package org.epilink.bot.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.*
import io.ktor.request.ContentTransformationException
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.coroutineScope
import org.epilink.bot.LinkDisplayableException
import org.epilink.bot.LinkException
import org.epilink.bot.LinkServerEnvironment
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.User
import org.epilink.bot.discord.LinkDiscordBot
import org.epilink.bot.http.data.*
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.http.sessions.RegisterSession
import org.epilink.bot.logger
import org.koin.core.KoinComponent
import org.koin.core.inject

/**
 * The back-end, defining API endpoints and more
 */
class LinkBackEnd : KoinComponent {
    /**
     * The environment the back end lives in
     */
    private val env: LinkServerEnvironment by inject()

    private val db: LinkServerDatabase by inject()

    private val discord: LinkDiscordBot by inject()

    private val discordBackEnd: LinkDiscordBackEnd by inject()

    private val microsoftBackEnd: LinkMicrosoftBackEnd by inject()

    /**
     * Defines the API endpoints. Served under /api/v1
     *
     * Anything responded in here SHOULD use [ApiResponse] in JSON form.
     */
    fun Route.epilinkApiV1() {

        // Make sure that exceptions are not left for Ktor to try and figure out.
        // Ktor would figure it out, but we a) need to log them b) respond with an ApiResponse
        intercept(ApplicationCallPipeline.Monitoring) {
            try {
                coroutineScope {
                    proceed()
                }
            } catch (ex: Exception) {
                logger.error(
                    "Uncaught exception encountered while processing v1 API call. Catch it and return a proper thing!",
                    ex
                )
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse(false, "Programming is so hard we have no idea of what happened just now. Retry maybe?")
                )
            }
        }

        route("meta") {
            @ApiEndpoint("GET /api/v1/meta/info")
            get("info") {
                call.respond(ApiResponse(true, data = getInstanceInformation()))
            }
        }

        route("user") {
            intercept(ApplicationCallPipeline.Features) {
                if (call.sessions.get<ConnectedSession>() == null) {
                    call.respond(HttpStatusCode.Unauthorized, ApiResponse(false, "You are not authenticated."))
                    return@intercept finish()
                }
                proceed()
            }

            @ApiEndpoint("GET /api/v1/user")
            get {
                val session = call.sessions.get<ConnectedSession>()!!
                call.respondText("Connected as " + session.discordId, ContentType.Text.Plain)
            }
        }

        route("register") {
            @ApiEndpoint("GET /api/v1/register/info")
            get("info") {
                val session = call.sessions.getOrSet { RegisterSession() }
                call.respondRegistrationStatus(session)
            }

            @ApiEndpoint("DELETE /api/v1/register")
            delete {
                call.sessions.clear<RegisterSession>()
                call.respond(HttpStatusCode.OK)
            }

            @ApiEndpoint("POST /api/v1/register")
            post {
                val session = call.sessions.get<RegisterSession>()
                catchLinkErrors {
                    with(session) {
                        if (this == null)
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse(false, "Missing session header", null)
                            )
                        else if (discordId == null || email == null || microsoftUid == null) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse(false, "Incomplete registration process", session)
                            )
                        } else {
                            val options: AdditionalRegistrationOptions =
                                call.receiveCatching() ?: return@post
                            val u = db.createUser(this, options.keepIdentity)
                            discord.launchInScope {
                                discord.updateRoles(u, true)
                            }
                            call.loginAs(u)
                            call.respond(ApiResponse(true, "Account created, logged in."))
                        }
                    }
                }
            }
        }

        @ApiEndpoint("POST /register/authcode/discord")
        @ApiEndpoint("POST /register/authcode/msft")
        post("authcode/{service}") {
            catchLinkErrors {
                when (val service = call.parameters["service"]) {
                    null -> error("Invalid service") // Should not happen
                    "discord" -> processDiscordAuthCode(call, call.receive())
                    "msft" -> processMicrosoftAuthCode(call, call.receive())
                    else -> call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse(false, "Invalid service: $service", null)
                    )
                }
            }
        }
    }

    private suspend inline fun <reified T : Any> ApplicationCall.receiveCatching(): T? = try {
        receive()
    } catch (ex: ContentTransformationException) {
        logger.error("Incorrect input from user", ex)
        null
    }

    /**
     * Create an [InstanceInformation] object based on this back end's environment and configuration
     */
    private fun getInstanceInformation(): InstanceInformation =
        InstanceInformation(
            title = env.name,
            logo = null, // TODO add a cfg entry for the logo
            authorizeStub_msft = microsoftBackEnd.getAuthorizeStub(),
            authorizeStub_discord = discordBackEnd.getAuthorizeStub()
        )

    /**
     * Take a Discord authorization code, consume it and apply the information retrieve form it to the current
     * registration session
     */
    private suspend fun processDiscordAuthCode(call: ApplicationCall, authcode: RegistrationAuthCode) {
        val session = call.sessions.getOrSet { RegisterSession() }
        // Get token
        val token = discordBackEnd.getDiscordToken(authcode.code, authcode.redirectUri)
        // Get information
        val (id, username, avatarUrl) = discordBackEnd.getDiscordInfo(token)
        val user = db.getUser(id)
        if (user != null) {
            call.loginAs(user)
            call.respond(ApiResponse(true, "Logged in", RegistrationContinuation("login", null)))
        } else {
            val newSession = session.copy(discordUsername = username, discordId = id, discordAvatarUrl = avatarUrl)
            call.sessions.set(newSession)
            call.respond(
                ApiResponse(
                    true,
                    "Connected to Discord",
                    RegistrationContinuation("continue", newSession.toRegistrationInformation())
                )
            )
        }
    }

    /**
     * Take a Microsoft authorization code, consume it and apply the information retrieve form it to the current
     * registration session
     */
    private suspend fun processMicrosoftAuthCode(call: ApplicationCall, authcode: RegistrationAuthCode) {
        val session = call.sessions.getOrSet { RegisterSession() }
        // Get token
        val token = microsoftBackEnd.getMicrosoftToken(authcode.code, authcode.redirectUri)
        // Get information
        val (id, email) = microsoftBackEnd.getMicrosoftInfo(token)
        val adv = db.isAllowedToCreateAccount(null, id)
        if (adv is Disallowed) {
            call.respond(ApiResponse(false, adv.reason, null))
            return
        }
        val newSession = session.copy(email = email, microsoftUid = id)
        call.sessions.set(newSession)
        call.respond(
            ApiResponse(
                true,
                "Connected to Microsoft",
                RegistrationContinuation("continue", newSession.toRegistrationInformation())
            )
        )
    }

    /**
     * Setup the sessions to log in the passed user object
     */
    private fun ApplicationCall.loginAs(user: User) {
        sessions.clear<RegisterSession>()
        sessions.set(ConnectedSession(user.discordId))
    }

    private suspend fun ApplicationCall.respondRegistrationStatus(session: RegisterSession) {
        respond(ApiResponse(success = true, data = session.toRegistrationInformation()))
    }

    /**
     * Utility function for transforming a session into the JSON object that is returned by the back-end's APIs
     */
    private fun RegisterSession.toRegistrationInformation() =
        RegistrationInformation(email = email, discordAvatarUrl = discordAvatarUrl, discordUsername = discordUsername)

    private suspend inline fun PipelineContext<Unit, ApplicationCall>.catchLinkErrors(
        body: PipelineContext<Unit, ApplicationCall>.() -> Unit
    ) {
        try {
            body(this)
        } catch (ex: LinkDisplayableException) {
            logException(ex, true)
            call.respond(
                if (ex.isEndUserAtFault) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError,
                ApiResponse(false, ex.message)
            )
        } catch (ex: LinkException) {
            logException(ex)
            call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, "Encountered an internal error"))
        }
    }

    @PublishedApi
    internal fun logException(ex: Exception, debugOnly: Boolean = false) {
        if (debugOnly)
            logger.debug("Caught an exception", ex)
        else
            logger.error("Caught an exception", ex)
    }
}

/**
 * Utility function for appending classic OAuth parameters to a ParametersBuilder object all at once.
 */
fun ParametersBuilder.appendOauthParameters(
    clientId: String, secret: String, authcode: String, redirectUri: String
) {
    append("grant_type", "authorization_code")
    append("client_id", clientId)
    append("client_secret", secret)
    append("code", authcode)
    append("redirect_uri", redirectUri)
}

/**
 * Utility function for performing a GET on the given url (with the given bearer as the "Authorization: Bearer" header),
 * turning the JSON result into a map and returning that map.
 */
suspend fun HttpClient.getJson(url: String, bearer: String): Map<String, Any?> {
    val result = get<String>(url) {
        header("Authorization", "Bearer $bearer")
        header(HttpHeaders.Accept, ContentType.Application.Json)
    }
    return ObjectMapper().readValue(result)
}
