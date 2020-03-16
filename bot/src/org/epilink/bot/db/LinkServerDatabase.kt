package org.epilink.bot.db

import org.epilink.bot.config.LinkConfiguration
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.sql.Connection

/**
 * The class that manages the database and handles all business logic.
 *
 * Most of the functions declared here are suspending functions intended to be used
 * from within Ktor responses.
 */
class LinkServerDatabase(cfg: LinkConfiguration) {
    /**
     * The Database instance managed by JetBrains Exposed used for transactions.
     */
    @OptIn(UsesTrueIdentity::class) // Creation of TrueIdentities table
    private val db: Database =
        Database.connect("jdbc:sqlite:${cfg.db}", driver = "org.sqlite.JDBC")
            .apply {
                // Required for SQLite
                transactionManager.defaultIsolationLevel =
                    Connection.TRANSACTION_SERIALIZABLE

                // Create the tables if they do not already exist
                transaction(this) {
                    SchemaUtils.create(
                        Users,
                        TrueIdentities
                    )
                }
            }

    /*
     * All functions regarding user creation, deletion, retrieval etc., will be
     * added here as more high level functions. The business logic (e.g.
     * checking for bans, checking account validity...) will be done here. Only
     * code within this class is allowed to directly interact with the database.
     *
     * Most functions will be suspending functions, as Ktor uses coroutines.
     * (newSuspendedTransaction will be used to call JB Exposed stuff).
     */
    suspend fun countUsers(): Int =
        newSuspendedTransaction {
            User.count()
        }
}
