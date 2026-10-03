package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.DecoyNotesDatabaseProvider
import com.kitsune.core.data.local.entities.NoteEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val databaseProvider: DecoyNotesDatabaseProvider
) : NoteRepository {

    private val dao get() = databaseProvider.requireOpen().noteDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAll(): Flow<List<NoteEntity>> =
        databaseProvider.databaseState.flatMapLatest { db -> db?.noteDao()?.observeAll() ?: emptyFlow() }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSearch(query: String): Flow<List<NoteEntity>> =
        databaseProvider.databaseState.flatMapLatest { db -> db?.noteDao()?.observeSearch(query) ?: emptyFlow() }

    override suspend fun getById(id: String): NoteEntity? = dao.getById(id)

    override suspend fun upsert(note: NoteEntity) = dao.upsert(note)

    override suspend fun delete(note: NoteEntity) = dao.delete(note)
}
