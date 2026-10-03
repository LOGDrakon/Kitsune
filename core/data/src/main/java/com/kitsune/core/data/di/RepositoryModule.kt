package com.kitsune.core.data.di

import com.kitsune.core.data.repository.ChatImageRepository
import com.kitsune.core.data.repository.ChatImageRepositoryImpl
import com.kitsune.core.data.repository.ChatParticipantRepository
import com.kitsune.core.data.repository.ChatParticipantRepositoryImpl
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.ChatRepositoryImpl
import com.kitsune.core.data.repository.EntrySceneRepository
import com.kitsune.core.data.repository.MessageVariantRepository
import com.kitsune.core.data.repository.MessageVariantRepositoryImpl
import com.kitsune.core.data.repository.ToneCardRepository
import com.kitsune.core.data.repository.ToneCardRepositoryImpl
import com.kitsune.core.data.repository.EntrySceneRepositoryImpl
import com.kitsune.core.data.repository.FactionRepository
import com.kitsune.core.data.repository.FactionRepositoryImpl
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.data.repository.GenerationJobRepositoryImpl
import com.kitsune.core.data.repository.LocationRepository
import com.kitsune.core.data.repository.LocationRepositoryImpl
import com.kitsune.core.data.repository.LoreEntryRepository
import com.kitsune.core.data.repository.LoreEntryRepositoryImpl
import com.kitsune.core.data.repository.MemoryFragmentRepository
import com.kitsune.core.data.repository.MemoryFragmentRepositoryImpl
import com.kitsune.core.data.repository.MessageAuditLogRepository
import com.kitsune.core.data.repository.MessageAuditLogRepositoryImpl
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.data.repository.MessageRepositoryImpl
import com.kitsune.core.data.repository.ModerationLogRepository
import com.kitsune.core.data.repository.ModerationLogRepositoryImpl
import com.kitsune.core.data.repository.NoteRepository
import com.kitsune.core.data.repository.NoteRepositoryImpl
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.NpcRepositoryImpl
import com.kitsune.core.data.repository.PersonaImageRepository
import com.kitsune.core.data.repository.PersonaImageRepositoryImpl
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.data.repository.PersonaRepositoryImpl
import com.kitsune.core.data.repository.UniverseImageRepository
import com.kitsune.core.data.repository.UniverseImageRepositoryImpl
import com.kitsune.core.data.repository.UniverseRepository
import com.kitsune.core.data.repository.UniverseRepositoryImpl
import com.kitsune.core.data.repository.KeyMomentRepository
import com.kitsune.core.data.repository.KeyMomentRepositoryImpl
import com.kitsune.core.data.repository.StoryChapterRepository
import com.kitsune.core.data.repository.StoryChapterRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPersonaRepository(impl: PersonaRepositoryImpl): PersonaRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindLoreEntryRepository(impl: LoreEntryRepositoryImpl): LoreEntryRepository

    @Binds
    @Singleton
    abstract fun bindModerationLogRepository(impl: ModerationLogRepositoryImpl): ModerationLogRepository

    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindMemoryFragmentRepository(impl: MemoryFragmentRepositoryImpl): MemoryFragmentRepository

    @Binds
    @Singleton
    abstract fun bindEntrySceneRepository(impl: EntrySceneRepositoryImpl): EntrySceneRepository

    @Binds
    abstract fun bindToneCardRepository(impl: ToneCardRepositoryImpl): ToneCardRepository

    @Binds
    abstract fun bindMessageVariantRepository(impl: MessageVariantRepositoryImpl): MessageVariantRepository

    @Binds
    @Singleton
    abstract fun bindPersonaImageRepository(impl: PersonaImageRepositoryImpl): PersonaImageRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindFactionRepository(impl: FactionRepositoryImpl): FactionRepository

    @Binds
    @Singleton
    abstract fun bindGenerationJobRepository(impl: GenerationJobRepositoryImpl): GenerationJobRepository

    @Binds
    @Singleton
    abstract fun bindNpcRepository(impl: NpcRepositoryImpl): NpcRepository

    @Binds
    @Singleton
    abstract fun bindUniverseRepository(impl: UniverseRepositoryImpl): UniverseRepository

    @Binds
    @Singleton
    abstract fun bindChatParticipantRepository(impl: ChatParticipantRepositoryImpl): ChatParticipantRepository

    @Binds
    @Singleton
    abstract fun bindChatImageRepository(impl: ChatImageRepositoryImpl): ChatImageRepository

    @Binds
    @Singleton
    abstract fun bindUniverseImageRepository(impl: UniverseImageRepositoryImpl): UniverseImageRepository

    @Binds
    @Singleton
    abstract fun bindKeyMomentRepository(impl: KeyMomentRepositoryImpl): KeyMomentRepository

    @Binds
    @Singleton
    abstract fun bindStoryChapterRepository(impl: StoryChapterRepositoryImpl): StoryChapterRepository

    @Binds
    @Singleton
    abstract fun bindMessageAuditLogRepository(impl: MessageAuditLogRepositoryImpl): MessageAuditLogRepository
}
