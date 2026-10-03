package com.kitsune.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kitsune.core.data.local.database.GenerationJobDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Adds `proposalCount`/`templateStyleHint` (retry support) and `errorCategory` (failure
 * classification — see `GenerationFailureCategory`) to `generation_jobs`. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `generation_jobs` ADD COLUMN `proposalCount` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `generation_jobs` ADD COLUMN `templateStyleHint` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `generation_jobs` ADD COLUMN `errorCategory` TEXT DEFAULT NULL")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object GenerationJobDatabaseModule {

    @Provides
    @Singleton
    fun provideGenerationJobDatabase(@ApplicationContext context: Context): GenerationJobDatabase {
        return Room.databaseBuilder(
            context,
            GenerationJobDatabase::class.java,
            GenerationJobDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
