package com.kitsune.core.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kitsune.core.data.local.converters.Converters
import com.kitsune.core.data.local.dao.ChatDao
import com.kitsune.core.data.local.dao.ChatImageDao
import com.kitsune.core.data.local.dao.ChatParticipantDao
import com.kitsune.core.data.local.dao.EntrySceneDao
import com.kitsune.core.data.local.dao.MessageVariantDao
import com.kitsune.core.data.local.dao.ToneCardDao
import com.kitsune.core.data.local.dao.FactionDao
import com.kitsune.core.data.local.dao.LocationDao
import com.kitsune.core.data.local.dao.LoreEntryDao
import com.kitsune.core.data.local.dao.MemoryFragmentDao
import com.kitsune.core.data.local.dao.MessageAuditLogDao
import com.kitsune.core.data.local.dao.MessageDao
import com.kitsune.core.data.local.dao.ModerationLogDao
import com.kitsune.core.data.local.dao.NpcDao
import com.kitsune.core.data.local.dao.PersonaDao
import com.kitsune.core.data.local.dao.PersonaImageDao
import com.kitsune.core.data.local.dao.UniverseDao
import com.kitsune.core.data.local.dao.UniverseImageDao
import com.kitsune.core.data.local.dao.KeyMomentDao
import com.kitsune.core.data.local.dao.StoryChapterDao
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatImageEntity
import com.kitsune.core.data.local.entities.ChatParticipantEntity
import com.kitsune.core.data.local.entities.EntrySceneEntity
import com.kitsune.core.data.local.entities.MessageVariantEntity
import com.kitsune.core.data.local.entities.ToneCardEntity
import com.kitsune.core.data.local.entities.FactionEntity
import com.kitsune.core.data.local.entities.LocationEntity
import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MessageAuditLogEntity
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.ModerationLogEntity
import com.kitsune.core.data.local.entities.NpcEntity
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.PersonaImageEntity
import com.kitsune.core.data.local.entities.UniverseEntity
import com.kitsune.core.data.local.entities.UniverseImageEntity
import com.kitsune.core.data.local.entities.KeyMomentEntity
import com.kitsune.core.data.local.entities.StoryChapterEntity

@Database(
    entities = [
        PersonaEntity::class,
        UniverseEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        LoreEntryEntity::class,
        ModerationLogEntity::class,
        MemoryFragmentEntity::class,
        EntrySceneEntity::class,
        ToneCardEntity::class,
        MessageVariantEntity::class,
        PersonaImageEntity::class,
        LocationEntity::class,
        FactionEntity::class,
        NpcEntity::class,
        ChatParticipantEntity::class,
        ChatImageEntity::class,
        UniverseImageEntity::class,
        KeyMomentEntity::class,
        MessageAuditLogEntity::class,
        StoryChapterEntity::class
    ],
    version = 37,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class KitsuneDatabase : RoomDatabase() {
    abstract fun personaDao(): PersonaDao
    abstract fun universeDao(): UniverseDao
    abstract fun chatDao(): ChatDao
    abstract fun chatParticipantDao(): ChatParticipantDao
    abstract fun messageDao(): MessageDao
    abstract fun loreEntryDao(): LoreEntryDao
    abstract fun moderationLogDao(): ModerationLogDao
    abstract fun memoryFragmentDao(): MemoryFragmentDao
    abstract fun entrySceneDao(): EntrySceneDao

    abstract fun toneCardDao(): ToneCardDao

    abstract fun messageVariantDao(): MessageVariantDao
    abstract fun personaImageDao(): PersonaImageDao
    abstract fun locationDao(): LocationDao
    abstract fun factionDao(): FactionDao
    abstract fun npcDao(): NpcDao
    abstract fun chatImageDao(): ChatImageDao
    abstract fun universeImageDao(): UniverseImageDao
    abstract fun keyMomentDao(): KeyMomentDao
    abstract fun storyChapterDao(): StoryChapterDao
    abstract fun messageAuditLogDao(): MessageAuditLogDao

    companion object {
        const val DATABASE_NAME = "kitsune.db"
    }
}
