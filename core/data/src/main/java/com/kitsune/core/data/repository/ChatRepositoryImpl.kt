package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatMode
import com.kitsune.core.data.local.entities.ChatParticipantEntity
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.ParticipantType
import com.kitsune.core.security.storage.EncryptedImageStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider,
    private val encryptedImageStore: EncryptedImageStore
) : ChatRepository {

    private val dao get() = databaseProvider.requireOpen().chatDao()
    private val messageDao get() = databaseProvider.requireOpen().messageDao()
    private val chatParticipantDao get() = databaseProvider.requireOpen().chatParticipantDao()
    private val loreEntryDao get() = databaseProvider.requireOpen().loreEntryDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActive(): Flow<List<ChatEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.chatDao()?.observeActive() ?: emptyFlow()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByPersona(personaId: String): Flow<List<ChatEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.chatDao()?.observeByPersona(personaId) ?: emptyFlow()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByUniverse(universeId: String): Flow<List<ChatEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.chatDao()?.observeByUniverse(universeId) ?: emptyFlow()
        }

    override suspend fun getById(id: String): ChatEntity? = dao.getById(id)

    override suspend fun createChat(personaId: String, title: String, selectedSceneId: String?): ChatEntity {
        val now = System.currentTimeMillis()
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            universeId = null,
            personaId = personaId,
            title = title,
            mode = ChatMode.CHAT,
            selectedSceneId = selectedSceneId,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(chat)
        return chat
    }

    override suspend fun createUniverseChat(universeId: String, title: String, cast: List<Pair<ParticipantType, String>>): ChatEntity {
        val now = System.currentTimeMillis()
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            universeId = universeId,
            personaId = null,
            title = title,
            mode = ChatMode.CHAT,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(chat)
        cast.forEach { (type, participantId) ->
            chatParticipantDao.insert(
                ChatParticipantEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = chat.id,
                    participantType = type,
                    participantId = participantId,
                    createdAt = now
                )
            )
        }
        // Prime the new ensemble chat with the universe's existing lore so the model knows the
        // setting and characters from the very first message.
        loreEntryDao.getByUniverse(universeId).forEach { entry ->
            loreEntryDao.upsert(
                entry.copy(
                    id = UUID.randomUUID().toString(),
                    universeId = null,
                    chatId = chat.id,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        return chat
    }

    override suspend fun forkChat(sourceChatId: String, fromMessageId: String): ChatEntity? {
        val sourceChat = dao.getById(sourceChatId) ?: return null
        val forkMessage = messageDao.getById(fromMessageId) ?: return null

        val now = System.currentTimeMillis()
        val newChat = ChatEntity(
            id = UUID.randomUUID().toString(),
            universeId = sourceChat.universeId,
            personaId = sourceChat.personaId,
            title = "${sourceChat.title} (branche)",
            mode = sourceChat.mode,
            selectedSceneId = sourceChat.selectedSceneId,
            branchedFromMessageId = fromMessageId,
            // A branch continues the same story, so it must continue it in the same voice.
            // Until 2026-08-23 none of this was copied: forking a carefully configured conversation
            // silently reset every style setting to DEFAULT, and the branch started writing in a
            // different register from the message it forked at — while showing the transcript that
            // proved it used to write otherwise. `hasSeenStoryCard` rides along for the same reason:
            // the framing question was already answered for this story.
            storyPaceMode = sourceChat.storyPaceMode,
            toneMode = sourceChat.toneMode,
            involvementMode = sourceChat.involvementMode,
            narrativeRhythmMode = sourceChat.narrativeRhythmMode,
            universeMode = sourceChat.universeMode,
            intensityMode = sourceChat.intensityMode,
            customExperienceDirective = sourceChat.customExperienceDirective,
            replyLength = sourceChat.replyLength,
            narrationBalance = sourceChat.narrationBalance,
            voiceMode = sourceChat.voiceMode,
            stylePackId = sourceChat.stylePackId,
            storyPresetId = sourceChat.storyPresetId,
            hasSeenStoryCard = true,
            storyTimeAnchor = sourceChat.storyTimeAnchor,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(newChat)

        val messages = messageDao.getMessagesUpTo(sourceChatId, forkMessage.createdAt)
        messages.forEach { msg ->
            val copy = msg.copy(
                id = UUID.randomUUID().toString(),
                chatId = newChat.id
            )
            messageDao.upsert(copy)
        }

        return newChat
    }

    override suspend fun getBranchFamilyChats(chatId: String): List<ChatEntity> {
        val chat = dao.getById(chatId) ?: return emptyList()
        return when {
            chat.personaId != null -> observeByPersona(chat.personaId).first()
            chat.universeId != null -> observeByUniverse(chat.universeId).first()
            else -> listOf(chat)
        }
    }

    override suspend fun getParentChatId(chat: ChatEntity): String? {
        val branchPointMessageId = chat.branchedFromMessageId ?: return null
        return messageDao.getById(branchPointMessageId)?.chatId
    }

    override suspend fun upsert(chat: ChatEntity) = dao.upsert(chat)

    override suspend fun delete(chat: ChatEntity) = dao.delete(chat)
    
    override suspend fun deleteChatWithMessages(chat: ChatEntity) {
        messageDao.deleteAllForChat(chat.id)
        // Supprimer l'image de fond via EncryptedImageStore
        chat.backgroundImageId?.let { imageId ->
            runCatching { encryptedImageStore.delete(imageId) }
        }
        dao.delete(chat)
    }
}
