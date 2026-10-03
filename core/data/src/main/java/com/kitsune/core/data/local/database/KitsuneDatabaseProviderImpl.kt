package com.kitsune.core.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kitsune.core.common.coroutines.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase as SqlCipherDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Embeds [this] as a single-quoted SQL string literal (doubling any embedded quotes) — used for
 * `ATTACH DATABASE`/`KEY` clauses in [KitsuneDatabaseProviderImpl.exportPlaintextCopy]/
 * [KitsuneDatabaseProviderImpl.importPlaintextCopy], where parameter binding for `ATTACH` is not
 * reliably supported by every SQLite/SQLCipher driver layer — literal SQL text, exactly as
 * SQLCipher's own documentation shows it, is the safer choice here. Every value passed through
 * this in this file is app-controlled (a `context.filesDir`-derived path, or our own hex key
 * literal), never user input, so there is no injection surface.
 */
private fun String.asSqlStringLiteral(): String = "'" + replace("'", "''") + "'"

/**
 * BUG-034 (see BUGS.md): SQLCipher only skips its own internal PBKDF2 key-stretching when the key
 * buffer's *content* is exactly `x'<hex>'` (verified against the real `libsqlcipher.so`'s
 * `cipher_codec_key_derive` logic, not assumed) — this check applies identically whether the bytes
 * arrive via `sqlite3_key()`/`SupportOpenHelperFactory` ([KitsuneDatabaseProviderImpl.open]) or via
 * a `PRAGMA rekey` SQL string ([KitsuneDatabaseProviderImpl.rekey]). Passing this app's
 * already-strong HKDF-derived passphrase as raw bytes does NOT match that pattern, so SQLCipher
 * silently treats it as a low-entropy *password* and stretches it via PBKDF2 against a salt stored
 * in the database file — a completely different actual cipher key than the exact same passphrase
 * bytes wrapped in this literal. `open` and `rekey` must always agree on this convention, or the
 * very first security-mode switch (the first time `rekey` ever runs) silently desynchronizes the
 * database from what the next `open` call derives, with no warning until the next unlock attempt.
 */
internal fun ByteArray.toRawKeyLiteralText(): String = "x'" + joinToString("") { "%02x".format(it) } + "'"
internal fun ByteArray.toRawKeyLiteral(): ByteArray = toRawKeyLiteralText().toByteArray(Charsets.US_ASCII)

@Singleton
class KitsuneDatabaseProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider
) : KitsuneDatabaseProvider {

    @Volatile
    private var database: KitsuneDatabase? = null

    private val _databaseState = MutableStateFlow<KitsuneDatabase?>(null)
    override val databaseState: StateFlow<KitsuneDatabase?> = _databaseState.asStateFlow()

    init {
        System.loadLibrary("sqlcipher")
    }

    override fun isOpen(): Boolean = database != null

    override fun open(passphrase: ByteArray): KitsuneDatabase {
        database?.let { return it }
        synchronized(this) {
            database?.let { return it }
            val factory = SupportOpenHelperFactory(passphrase.toRawKeyLiteral())
            val opened = Room.databaseBuilder(context, KitsuneDatabase::class.java, KitsuneDatabase.DATABASE_NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36)
                .build()
            database = opened
            _databaseState.value = opened
            return opened
        }
    }

    override fun requireOpen(): KitsuneDatabase =
        database ?: error("KitsuneDatabase accessed before the vault was unlocked")

    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        requireOpen().withTransaction(block)

    override suspend fun rekey(newPassphrase: ByteArray) = withContext(dispatchers.io) {
        val writable = requireOpen().openHelper.writableDatabase
        // BUG-034 follow-up: rekeying via a hand-built `execSQL("PRAGMA rekey = ...")` string threw
        // "Queries can be performed using SQLiteDatabase query or rawQuery methods only." — this
        // PRAGMA returns a result row (like `PRAGMA journal_mode` does), which `execSQL` refuses to
        // run. `SupportHelper.getWritableDatabase()` always returns the concrete SQLCipher
        // SQLiteDatabase (verified against the library's own source, not assumed), which exposes a
        // proper `changePassword(ByteArray)` — it reconfigures the connection pool with the new key
        // directly (no SQL text involved at all), and applies the exact same raw-key-literal
        // convention as `open()` above.
        (writable as SqlCipherDatabase).changePassword(newPassphrase.toRawKeyLiteral())
        // Confirm the connection is still healthy and the new key actually works before the caller
        // trusts it.
        writable.query("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
        Unit
    }

    override suspend fun exportPlaintextCopy(destination: File) = withContext(dispatchers.io) {
        val writable = requireOpen().openHelper.writableDatabase as SqlCipherDatabase
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()
        destination.createNewFile()
        // Standard SQLCipher decrypt-to-plaintext-file recipe: attach a new database with an empty
        // key (SQLCipher's documented convention for "no encryption"), then let sqlcipher_export
        // copy every table/index/schema from the currently-open (already-unlocked) connection into
        // it — a full SQL-level copy, not a raw file operation, so Room's own bookkeeping tables
        // come along unchanged too.
        writable.execSQL("ATTACH DATABASE ${destination.absolutePath.asSqlStringLiteral()} AS plaintext_export KEY ''")
        try {
            // Same BUG-034-follow-up issue as rekey() above: sqlcipher_export() returns a result
            // row, so execSQL rejects it ("Queries can be performed using SQLiteDatabase query or
            // rawQuery methods only.") — must go through query(), even though we don't need the row.
            writable.query("SELECT sqlcipher_export('plaintext_export')").use { it.moveToFirst() }
        } finally {
            writable.execSQL("DETACH DATABASE plaintext_export")
        }
        Unit
    }

    override suspend fun importPlaintextCopy(source: File, newPassphrase: ByteArray) = withContext(dispatchers.io) {
        check(!isOpen()) { "importPlaintextCopy must run before any open() call in this process" }
        val finalFile = context.getDatabasePath(KitsuneDatabase.DATABASE_NAME)
        finalFile.parentFile?.mkdirs()
        // BUG (found via logcat on a real device): deleting only the main .db file can leave its
        // WAL-mode -wal/-shm siblings behind (e.g. from a previous attempt on this same device, or
        // from Room ever briefly touching this path) — SQLCipher then fails to (re)create the main
        // file at this path via ATTACH DATABASE while that residue lingers
        // (SQLiteCantOpenDatabaseException / "unable to open database", code 14). Clear all three.
        listOf(finalFile, File("${finalFile.path}-wal"), File("${finalFile.path}-shm"), File("${finalFile.path}-journal"))
            .forEach { if (it.exists()) it.delete() }
        // BUG (found via logcat on a real device, second occurrence after the cleanup above):
        // `ATTACH DATABASE '<non-existent file>' AS x KEY 'x...'` still failed with
        // SQLiteCantOpenDatabaseException/"unable to open database" (code 14) — the underlying
        // os_unix.c open() reported ENOENT even though the parent directory demonstrably existed
        // and was writable (verified with `adb shell run-as ... touch` on the affected device).
        // A *keyed* ATTACH needs to write SQLCipher's encryption header immediately (unlike a
        // keyless/plaintext ATTACH, which can lazily create the file on first write — consistent
        // with exportPlaintextCopy above, which uses `KEY ''` and was not observed to fail), and
        // that appears to require the target file to already exist on this device/SQLCipher combo
        // rather than being created by ATTACH itself. Pre-creating an empty file works around it.
        finalFile.createNewFile()

        // Mirror image of exportPlaintextCopy: open the plaintext file directly (empty password =
        // unencrypted, same SQLCipher convention), then sqlcipher_export it INTO a freshly attached
        // database keyed with newPassphrase — producing a normal, fully SQLCipher-encrypted file at
        // this device's real database path.
        val plaintextDb = SqlCipherDatabase.openDatabase(
            source.absolutePath, "", null, SqlCipherDatabase.OPEN_READWRITE, null
        )
        try {
            // BUG-034 convention (see the class-level comment on toRawKeyLiteralText/toRawKeyLiteral
            // above): the key MUST be inlined as literal `x'<hex>'` SQL text, never bound as a
            // parameter — a bound parameter is treated as an ordinary low-entropy password and gets
            // PBKDF2-stretched, silently producing a different actual cipher key than open()/rekey()
            // expect, exactly the desync BUG-034 was about.
            //
            // BUG-053 follow-up: the previous version inlined this as a bare `x'<hex>'` token —
            // which SQL parses as a BLOB LITERAL, delivering already-decoded raw bytes to
            // SQLCipher's key handler. BUG-034's own finding is that SQLCipher only skips PBKDF2
            // when the key buffer's CONTENT textually matches "x'<hex>'" as ASCII text; a blob's
            // raw decoded bytes don't look like that ASCII text at all, so SQLCipher silently
            // PBKDF2-stretched them as a password instead — a DIFFERENT actual key than open()'s
            // `SupportOpenHelperFactory(passphrase.toRawKeyLiteral())` (which passes the ASCII
            // text "x'<hex>'" as a byte buffer, correctly matching the pattern). Wrapping the
            // literal as a genuine SQL *string* (via asSqlStringLiteral(), content = "x'<hex>'")
            // makes SQLCipher receive the same ASCII-text form open()/rekey() already use.
            val keyLiteral = newPassphrase.toRawKeyLiteralText().asSqlStringLiteral()
            plaintextDb.execSQL("ATTACH DATABASE ${finalFile.absolutePath.asSqlStringLiteral()} AS encrypted_import KEY $keyLiteral")
            try {
                // Same fix as exportPlaintextCopy above — sqlcipher_export() must go through
                // query(), execSQL rejects any statement that returns a result row.
                plaintextDb.query("SELECT sqlcipher_export('encrypted_import')").use { it.moveToFirst() }
            } finally {
                plaintextDb.execSQL("DETACH DATABASE encrypted_import")
            }
        } finally {
            plaintextDb.close()
        }
        Unit
    }

    override fun close() {
        database?.close()
        database = null
        _databaseState.value = null
    }

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `entry_scenes` (
                        `id` TEXT NOT NULL,
                        `personaId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `scenario` TEXT NOT NULL,
                        `firstMessage` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`personaId`) REFERENCES `personas`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_scenes_personaId` ON `entry_scenes` (`personaId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `persona_images` (
                        `id` TEXT NOT NULL,
                        `personaId` TEXT NOT NULL,
                        `imageStoreId` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`personaId`) REFERENCES `personas`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_persona_images_personaId` ON `persona_images` (`personaId`)")

                db.execSQL("ALTER TABLE `chats` ADD COLUMN `selectedSceneId` TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `branchedFromMessageId` TEXT DEFAULT NULL")
                
                // Créer la table universes si elle n'existe pas
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `universes` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `genre` TEXT NOT NULL,
                        `visualStyle` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_universes_name` ON `universes` (`name`)")
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `locations` (
                        `id` TEXT NOT NULL,
                        `universeId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `parentLocationId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`universeId`) REFERENCES `universes`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_locations_universeId` ON `locations` (`universeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_locations_name` ON `locations` (`name`)")
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `factions` (
                        `id` TEXT NOT NULL,
                        `universeId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `alignment` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`universeId`) REFERENCES `universes`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_factions_universeId` ON `factions` (`universeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_factions_name` ON `factions` (`name`)")
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `npcs` (
                        `id` TEXT NOT NULL,
                        `universeId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `personality` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `factionId` TEXT,
                        `locationId` TEXT,
                        `age` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`universeId`) REFERENCES `universes`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`factionId`) REFERENCES `factions`(`id`) ON DELETE SET NULL,
                        FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_npcs_universeId` ON `npcs` (`universeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_npcs_factionId` ON `npcs` (`factionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_npcs_locationId` ON `npcs` (`locationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_npcs_name` ON `npcs` (`name`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensemble/universe chats (no single protagonist persona) — cast lives here rather
                // than as a column on `chats` since it mixes personas and NPCs and is unbounded.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_participants` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `participantType` TEXT NOT NULL,
                        `participantId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_participants_chatId` ON `chat_participants` (`chatId`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Per-chat gallery (any image generated in the conversation, whether or not it's
                // attributed to a specific persona) and per-universe gallery (group scenes with no
                // single persona to attribute to) — see FEATURES.md section 5/6.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_images` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `imageStoreId` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_images_chatId` ON `chat_images` (`chatId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `universe_images` (
                        `id` TEXT NOT NULL,
                        `universeId` TEXT NOT NULL,
                        `imageStoreId` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`universeId`) REFERENCES `universes`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_universe_images_universeId` ON `universe_images` (`universeId`)")

                // Retroactive backfill: every image already sent into a conversation before this
                // gallery existed becomes a chat_images row (and, for ensemble/universe chats, also
                // a universe_images row) — reusing the message's own id as the new row's primary key
                // since each message has at most one image, so it's already unique per source row.
                db.execSQL("""
                    INSERT INTO `chat_images` (`id`, `chatId`, `imageStoreId`, `description`, `createdAt`)
                    SELECT `id`, `chatId`, `imageAttachmentPath`, `content`, `createdAt`
                    FROM `messages`
                    WHERE `imageAttachmentPath` IS NOT NULL
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `universe_images` (`id`, `universeId`, `imageStoreId`, `description`, `createdAt`)
                    SELECT `messages`.`id`, `chats`.`universeId`, `messages`.`imageAttachmentPath`, `messages`.`content`, `messages`.`createdAt`
                    FROM `messages`
                    INNER JOIN `chats` ON `messages`.`chatId` = `chats`.`id`
                    WHERE `messages`.`imageAttachmentPath` IS NOT NULL AND `chats`.`universeId` IS NOT NULL
                """.trimIndent())
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Tracks how much the story has actually used an NPC (seeded/updated from
                // LoreEntryEntity.version by SyncCastFromLoreUseCase) so NPC pickers can sort
                // recurring/important characters above one-off background ones instead of plain
                // alphabetical order.
                db.execSQL("ALTER TABLE `npcs` ADD COLUMN `importance` INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `objectives` (
                        `id` TEXT NOT NULL,
                        `personaId` TEXT,
                        `chatId` TEXT,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `progress` INTEGER NOT NULL DEFAULT 0,
                        `priority` TEXT NOT NULL,
                        `isSecret` INTEGER NOT NULL DEFAULT 0,
                        `isAutoGenerated` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`personaId`) REFERENCES `personas`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_objectives_personaId` ON `objectives` (`personaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_objectives_chatId` ON `objectives` (`chatId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_objectives_status` ON `objectives` (`status`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `character_journals` (
                        `id` TEXT NOT NULL,
                        `personaId` TEXT,
                        `chatId` TEXT,
                        `entry` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`personaId`) REFERENCES `personas`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_journals_personaId` ON `character_journals` (`personaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_journals_chatId` ON `character_journals` (`chatId`)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `character_relationships` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `targetName` TEXT NOT NULL,
                        `trust` REAL NOT NULL DEFAULT 0,
                        `affection` REAL NOT NULL DEFAULT 0,
                        `tension` REAL NOT NULL DEFAULT 0,
                        `respect` REAL NOT NULL DEFAULT 0,
                        `dominantImpression` TEXT NOT NULL DEFAULT '',
                        `isAutoGenerated` INTEGER NOT NULL DEFAULT 0,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_relationships_chatId` ON `character_relationships` (`chatId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_character_relationships_names` ON `character_relationships` (`chatId`, `sourceName`, `targetName`)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `character_moods` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `moodName` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `valence` REAL NOT NULL DEFAULT 0,
                        `arousal` REAL NOT NULL DEFAULT 0.5,
                        `dominance` REAL NOT NULL DEFAULT 0.5,
                        `intensity` REAL NOT NULL DEFAULT 0.5,
                        `isAutoGenerated` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_moods_chatId` ON `character_moods` (`chatId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_character_moods_source` ON `character_moods` (`chatId`, `sourceName`)")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `story_projects` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `completionEstimate` INTEGER NOT NULL DEFAULT 0,
                        `isSecret` INTEGER NOT NULL DEFAULT 0,
                        `isAutoGenerated` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_story_projects_chatId` ON `story_projects` (`chatId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_story_projects_status` ON `story_projects` (`chatId`, `status`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `contradictions` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `severity` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `isAutoGenerated` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `resolvedAt` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contradictions_chatId` ON `contradictions` (`chatId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contradictions_status` ON `contradictions` (`chatId`, `status`)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `hasSeenBriefing` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `character_values` (
                        `id` TEXT NOT NULL,
                        `participantType` TEXT NOT NULL,
                        `participantId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `weight` INTEGER NOT NULL DEFAULT 3,
                        `isAutoGenerated` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_values_participant` ON `character_values` (`participantType`, `participantId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_character_values_participant_title` ON `character_values` (`participantType`, `participantId`, `title`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `character_value_conflicts` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `valueTitle` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `severity` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `isAutoGenerated` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `resolvedAt` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_value_conflicts_chatId` ON `character_value_conflicts` (`chatId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_value_conflicts_chatId_status` ON `character_value_conflicts` (`chatId`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_value_conflicts_chatId_source_value` ON `character_value_conflicts` (`chatId`, `sourceName`, `valueTitle`)")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `generation_jobs` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `universeId` TEXT,
                        `chatId` TEXT,
                        `fromNpcId` TEXT,
                        `resultJson` TEXT,
                        `errorMessage` TEXT,
                        `notificationShown` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_generation_jobs_type` ON `generation_jobs` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_generation_jobs_state` ON `generation_jobs` (`state`)")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Tracks how a generated NPC should be folded into the scene (RETROACTIVE / ARRIVAL)
                // so the chat ViewModel can apply the framing when it consumes the completed job.
                db.execSQL("ALTER TABLE `generation_jobs` ADD COLUMN `joinFraming` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_generation_jobs_chatId` ON `generation_jobs` (`chatId`)")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `generation_jobs`")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `lastChunkIndexedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP TABLE IF EXISTS `objectives`")
                db.execSQL("DROP TABLE IF EXISTS `character_journals`")
                db.execSQL("DROP TABLE IF EXISTS `character_relationships`")
                db.execSQL("DROP TABLE IF EXISTS `character_moods`")
                db.execSQL("DROP TABLE IF EXISTS `story_projects`")
                db.execSQL("DROP TABLE IF EXISTS `contradictions`")
                db.execSQL("DROP TABLE IF EXISTS `character_values`")
                db.execSQL("DROP TABLE IF EXISTS `character_value_conflicts`")
                db.execSQL("DELETE FROM `memory_fragments`")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `key_moments` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `messageId` TEXT,
                        `momentType` TEXT NOT NULL,
                        `mood` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `snippets` TEXT NOT NULL,
                        `isAutoDetected` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `momentOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_key_moments_chatId` ON `key_moments` (`chatId`)")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `personas` ADD COLUMN `sourceListingId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_personas_sourceListingId` ON `personas` (`sourceListingId`)")

                db.execSQL("ALTER TABLE `universes` ADD COLUMN `avatarImageId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `universes` ADD COLUMN `sourceListingId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_universes_sourceListingId` ON `universes` (`sourceListingId`)")

                db.execSQL("ALTER TABLE `npcs` ADD COLUMN `physicalDescription` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `npcs` ADD COLUMN `avatarImageId` TEXT DEFAULT NULL")

                db.execSQL("ALTER TABLE `locations` ADD COLUMN `avatarImageId` TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `personas` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `universes` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `storyPaceMode` TEXT NOT NULL DEFAULT 'DEFAULT'")
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `toneMode` TEXT NOT NULL DEFAULT 'DEFAULT'")
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `involvementMode` TEXT NOT NULL DEFAULT 'DEFAULT'")
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `narrativeRhythmMode` TEXT NOT NULL DEFAULT 'DEFAULT'")
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `universeMode` TEXT NOT NULL DEFAULT 'DEFAULT'")
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `intensityMode` TEXT NOT NULL DEFAULT 'DEFAULT'")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `personas` ADD COLUMN `contentLanguage` TEXT DEFAULT NULL")
            }
        }

        // Deliberately no foreign key / cascade — this table must survive deletion of the chats/
        // messages it mirrors (see MessageAuditLogEntity's doc comment).
        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `message_audit_log` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `messageId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_audit_log_chatId` ON `message_audit_log` (`chatId`)")
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `storyTimeAnchor` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `lore_entries` ADD COLUMN `occurredAt` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Chronology: gives key moments and event lore entries a *sortable* anchor next to their
         *  free-text in-fiction date, so `BuildStoryChronologyUseCase` can build an ordered ledger
         *  (FEATURES.md section 4). No backfill — legacy rows keep `anchorCreatedAt = 0`, tie, and
         *  fall back to `momentOrder`, which correctly places them first as the oldest.
         *
         *  Also swaps the messages index for a composite one: every memory query filters on chatId
         *  and orders on createdAt, and the composite covers the chatId-only lookups as a prefix. */
        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `key_moments` ADD COLUMN `anchorCreatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `key_moments` ADD COLUMN `storyTimeLabel` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `lore_entries` ADD COLUMN `anchorCreatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS `index_messages_chatId`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId_createdAt` ON `messages` (`chatId`, `createdAt`)")
            }
        }

        /** Hierarchical summary (`story_chapters`), lore alias resolution, and the derived-step
         *  cursor that finally makes BUG-015's lost batches retryable. */
        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `story_chapters` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `chapterIndex` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `fromCreatedAt` INTEGER NOT NULL,
                        `throughCreatedAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_story_chapters_chatId` ON `story_chapters` (`chatId`)")

                db.execSQL("ALTER TABLE `lore_entries` ADD COLUMN `aliases` TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE `chats` ADD COLUMN `derivedThroughCreatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `derivedRetryCount` INTEGER NOT NULL DEFAULT 0")

                // CRITICAL — do not remove. Without it every pre-existing chat reads its derived
                // cursor as 0, concludes that its entire history still needs lore/cast/index/
                // timeline catch-up, and replays it on the first message after the update: a
                // robustness feature turned into a burst of API calls on every long chat at once.
                db.execSQL("UPDATE `chats` SET `derivedThroughCreatedAt` = `summarizedThroughCreatedAt`")
            }
        }

        /** Free-form per-chat writing instruction typed in the "modes d'expérience" dialog
         *  (`ChatEntity.customExperienceDirective`). Same shape as MIGRATION_25_26, which added
         *  `storyTimeAnchor` to the same table: a plain nullable-free TEXT column defaulting to the
         *  empty string, so every pre-existing chat reads as "no custom instruction" with no
         *  backfill needed. */
        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `customExperienceDirective` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Profondeur narrative (2026-08-22) : intériorité des personas, fils narratifs et posture
         * relationnelle.
         *
         * Que des colonnes avec valeur par défaut, aucun backfill : un persona existant lit ses cinq
         * champs comme vides et le prompt omet simplement les sections correspondantes, une fiche de
         * lore existante n'est ni résolue ni porteuse de posture. Rien à recalculer, rien à perdre.
         *
         * `resolved` est stocké en INTEGER : SQLite n'a pas de type booléen, c'est la convention que
         * Room applique déjà pour `isAutoGenerated` sur cette même table.
         */
        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("desire", "fear", "flaw", "moralLine", "secret").forEach { column ->
                    db.execSQL("ALTER TABLE `personas` ADD COLUMN `$column` TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL("ALTER TABLE `lore_entries` ADD COLUMN `resolved` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `lore_entries` ADD COLUMN `stance` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Forme de réponse par conversation, pack de style par conversation, et drapeau de carte
         * d'histoire (2026-08-23).
         *
         * Tous les défauts reconduisent **exactement** le comportement d'avant :
         * - les trois modes à `DEFAULT` n'ajoutent aucune directive, comme les six modes de v22→v23 ;
         * - `stylePackId` vide signifie « utilise le pack appliqué globalement », c'est-à-dire le
         *   seul comportement qui existait jusqu'ici ;
         * - `hasSeenStoryCard = 0` fait que les conversations déjà commencées se verront proposer la
         *   carte une fois. C'est délibéré et non un effet de bord : ce sont précisément les
         *   conversations qui tournent aujourd'hui avec un contrat de style vide.
         *
         * `hasSeenStoryCard` est en INTEGER, comme `hasSeenBriefing` sur la même table — SQLite n'a
         * pas de booléen.
         */
        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "replyLength" to "DEFAULT",
                    "narrationBalance" to "DEFAULT",
                    "voiceMode" to "DEFAULT"
                ).forEach { (column, default) ->
                    db.execSQL("ALTER TABLE `chats` ADD COLUMN `$column` TEXT NOT NULL DEFAULT '$default'")
                }
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `stylePackId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `storyPresetId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `hasSeenStoryCard` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Fiches de ton par persona/univers (2026-08-24).
         *
         * Table neuve, donc rien à convertir : les personas existants n'ont simplement aucune fiche
         * et la carte d'histoire continue de ne proposer que les six préréglages intégrés.
         *
         * Le DDL est écrit à la main plutôt que copié d'un schéma exporté, donc il doit correspondre
         * **exactement** à ce que Room attend, index compris — un `CREATE INDEX` manquant fait échouer
         * la validation d'identité au premier lancement d'après-migration, pas au moment de la
         * migration. Vérifié par diff de `32.json` contre `33.json`.
         */
        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tone_cards` (
                        `id` TEXT NOT NULL,
                        `personaId` TEXT,
                        `universeId` TEXT,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `basePresetId` TEXT NOT NULL,
                        `storyPaceMode` TEXT NOT NULL,
                        `toneMode` TEXT NOT NULL,
                        `involvementMode` TEXT NOT NULL,
                        `narrativeRhythmMode` TEXT NOT NULL,
                        `universeMode` TEXT NOT NULL,
                        `intensityMode` TEXT NOT NULL,
                        `replyLength` TEXT NOT NULL,
                        `narrationBalance` TEXT NOT NULL,
                        `voiceMode` TEXT NOT NULL,
                        `directive` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`personaId`) REFERENCES `personas`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`universeId`) REFERENCES `universes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tone_cards_personaId` ON `tone_cards` (`personaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tone_cards_universeId` ON `tone_cards` (`universeId`)")
            }
        }

        /**
         * Couverture et dernière visite par histoire (2026-08-25).
         *
         * Les deux colonnes servent le même changement : l'accueil cesse d'être une liste de
         * conversations pour devenir une étagère d'histoires. `coverImageId` est nul jusqu'à la
         * fermeture du premier chapitre, `lastVisitedAt` vaut `0` pour tout ce qui existe déjà.
         *
         * Ce zéro est délibérément lisible comme « jamais ouvert depuis qu'on compte » plutôt que
         * comme une visite en 1970 : l'étagère n'affiche pas de pastille dans ce cas, sinon chaque
         * conversation ancienne s'annoncerait à tort comme ayant du nouveau au premier lancement
         * d'après-migration.
         */
        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `coverImageId` TEXT")
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `lastVisitedAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Lore rattachable à un personnage (2026-08-25).
         *
         * Troisième portée du lore, en miroir de celle des fiches de ton : le `character_book` d'une
         * carte importée est une connaissance que le personnage transporte, pas la propriété d'une
         * conversation qui n'existe pas encore ni d'un univers que l'importateur n'a jamais créé.
         *
         * Nullable et sans clé étrangère déclarée côté SQL par ce `ALTER TABLE` : SQLite ne sait pas
         * ajouter une contrainte de clé étrangère après coup, et Room ne la valide que sur le schéma
         * de la table. La colonne est donc simplement indexée, comme les deux autres portées.
         */
        private val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `lore_entries` ADD COLUMN `personaId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lore_entries_personaId` ON `lore_entries` (`personaId`)")
            }
        }

        /**
         * Variantes de réponse (2026-08-25).
         *
         * Table à part plutôt que colonnes sur `messages` : cette table-là est lue par le rewind, les
         * quatre couches de mémoire, l'export roman, le journal d'audit et l'aperçu de la liste de
         * conversations, chacun avec ses propres filtres et curseurs. Y ajouter un regroupement aurait
         * placé un nouvel invariant devant tous ces lecteurs d'un coup ; ici `messages.content` garde
         * exactement son sens — la version actuellement dans l'histoire.
         *
         * `ON DELETE CASCADE` : supprimer un message ou revenir en arrière emporte ses variantes,
         * ce qui est le comportement voulu et évite des orphelines invisibles.
         */
        private val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `message_variants` (
                        `id` TEXT NOT NULL,
                        `messageId` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_variants_messageId` ON `message_variants` (`messageId`)")
            }
        }
    }
}
