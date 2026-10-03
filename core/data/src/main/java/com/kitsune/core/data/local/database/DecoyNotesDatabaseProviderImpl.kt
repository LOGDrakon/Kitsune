package com.kitsune.core.data.local.database

import android.content.Context
import androidx.room.Room
import com.kitsune.core.common.coroutines.DispatcherProvider
import com.kitsune.core.data.local.entities.NoteEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase as SqlCipherDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecoyNotesDatabaseProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider
) : DecoyNotesDatabaseProvider {

    @Volatile
    private var database: DecoyNotesDatabase? = null

    private val _databaseState = MutableStateFlow<DecoyNotesDatabase?>(null)
    override val databaseState: StateFlow<DecoyNotesDatabase?> = _databaseState.asStateFlow()

    init {
        System.loadLibrary("sqlcipher")
    }

    override fun isOpen(): Boolean = database != null

    override suspend fun open(passphrase: ByteArray): DecoyNotesDatabase = withContext(dispatchers.io) {
        database?.let { return@withContext it }
        val isFirstEver = !context.getDatabasePath(DecoyNotesDatabase.DATABASE_NAME).exists()
        val opened = synchronized(this@DecoyNotesDatabaseProviderImpl) {
            database?.let { return@synchronized it }
            val factory = SupportOpenHelperFactory(passphrase.toRawKeyLiteral())
            Room.databaseBuilder(context, DecoyNotesDatabase::class.java, DecoyNotesDatabase.DATABASE_NAME)
                .openHelperFactory(factory)
                .build()
                .also {
                    database = it
                    _databaseState.value = it
                }
        }
        if (isFirstEver) opened.noteDao().insertAll(seedNotes())
        opened
    }

    override fun requireOpen(): DecoyNotesDatabase =
        database ?: error("DecoyNotesDatabase accessed before the decoy vault was unlocked")

    override fun close() {
        database?.close()
        database = null
        _databaseState.value = null
    }

    override suspend fun rekey(newPassphrase: ByteArray) = withContext(dispatchers.io) {
        val writable = requireOpen().openHelper.writableDatabase
        // Same convention as KitsuneDatabaseProviderImpl.rekey (BUG-034, see BUGS.md) — open() and
        // rekey() must always agree on the raw-key-literal format.
        (writable as SqlCipherDatabase).changePassword(newPassphrase.toRawKeyLiteral())
        writable.query("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
        Unit
    }

    /** 2-3 innocuous placeholder notes, inserted only the very first time the decoy database is
     *  created — never re-seeded, and never re-inserted if the user later deletes them (this only
     *  runs when [open] finds no existing database file). */
    private fun seedNotes(): List<NoteEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            NoteEntity(
                id = UUID.randomUUID().toString(),
                title = "Courses",
                body = "Pain\nLait\nŒufs\nCafé\nPâtes",
                createdAt = now,
                updatedAt = now
            ),
            NoteEntity(
                id = UUID.randomUUID().toString(),
                title = "À faire",
                body = "Appeler le garagiste\nRéserver le resto pour samedi\nPrendre rendez-vous chez le dentiste",
                createdAt = now,
                updatedAt = now
            )
        )
    }
}
