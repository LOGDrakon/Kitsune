package com.kitsune.core.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kitsune.core.data.local.converters.Converters
import com.kitsune.core.data.local.dao.GenerationJobDao
import com.kitsune.core.data.local.entities.GenerationJobEntity

/**
 * Separate, unencrypted Room database for tracking background AI generation jobs.
 *
 * It intentionally lives outside the SQLCipher-protected [KitsuneDatabase] because
 * [GenerationWorker] needs to read/write job state even when the app process is
 * relaunched in the background while the user-facing vault is locked. Attempting
 * to access the encrypted DB from the worker in that state makes the worker crash,
 * which leaves the job stuck in a "PENDING" Room row while WorkManager reports it
 * as FAILED.
 */
@Database(entities = [GenerationJobEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class GenerationJobDatabase : RoomDatabase() {
    abstract fun generationJobDao(): GenerationJobDao

    companion object {
        const val DATABASE_NAME = "generation_jobs.db"
    }
}
