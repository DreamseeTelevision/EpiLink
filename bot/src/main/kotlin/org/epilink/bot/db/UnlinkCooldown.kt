/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

import guru.zoroark.shedinja.environment.InjectionScope
import guru.zoroark.shedinja.environment.invoke
import guru.zoroark.shedinja.extensions.SynchronizedLazyPropertyWrapper
import guru.zoroark.shedinja.extensions.WrappedReadOnlyProperty
import guru.zoroark.shedinja.extensions.wrapIn
import org.epilink.bot.CacheClient
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.wrapInLazy
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import kotlin.properties.ReadOnlyProperty

/**
 * Interface for managing the unlink cooldown feature of EpiLink.
 *
 * Under certain circumstances, the user is prevented from remove their identity from the server. This is to prevent
 * potential abuse. This cooldown is engaged or refreshed upon:
 * - A ban
 * - An identity access
 *
 * Other reasons for this cooldown to be refreshed or engaged may be added later on.
 *
 * The duration of the cooldown is configured in [WebServerConfiguration]
 */
interface UnlinkCooldown {
    /**
     * True if the user can remove their identity from the server right now (i.e. cooldown has expired), false otherwise
     */
    suspend fun canUnlink(userId: String): Boolean

    /**
     * Refresh the cooldown (or engage it if not engaged yet) for the given user
     */
    suspend fun refreshCooldown(userId: String)

    /**
     * Remove the cooldown for the given user.
     */
    suspend fun deleteCooldown(userId: String)
}

internal class UnlinkCooldownImpl(scope: InjectionScope) : UnlinkCooldown {
    private val serverConfiguration: WebServerConfiguration by scope()
    private val storage: UnlinkCooldownStorage by scope<CacheClient>() wrapInLazy {
        it.newUnlinkCooldownStorage("el_ulc_")
    }

    override suspend fun canUnlink(userId: String): Boolean = storage.canUnlink(userId)

    override suspend fun refreshCooldown(userId: String) =
        storage.refreshCooldown(userId, serverConfiguration.unlinkCooldown)

    override suspend fun deleteCooldown(userId: String) =
        storage.refreshCooldown(userId, 0L)
}