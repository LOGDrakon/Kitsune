package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ParticipantType
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeActive(): Flow<List<ChatEntity>>

    fun observeByPersona(personaId: String): Flow<List<ChatEntity>>

    fun observeByUniverse(universeId: String): Flow<List<ChatEntity>>

    suspend fun getById(id: String): ChatEntity?

    suspend fun createChat(personaId: String, title: String, selectedSceneId: String? = null): ChatEntity

    /** Ensemble chat with no single protagonist persona: [cast] is the initial set of personas
     * and/or NPCs the AI will play together in this shared scene (FEATURES.md section 6). */
    suspend fun createUniverseChat(universeId: String, title: String, cast: List<Pair<ParticipantType, String>>): ChatEntity

    suspend fun forkChat(sourceChatId: String, fromMessageId: String): ChatEntity?

    /** Every chat that shares [chatId]'s persona (or universe, for an ensemble chat) — i.e. every
     * chat a branch-tree view of [chatId] could possibly need, in one query. Forking always
     * inherits `personaId`/`universeId` unchanged from the source chat ([forkChat]), so this is
     * guaranteed to include the whole branch family, not just direct siblings. */
    suspend fun getBranchFamilyChats(chatId: String): List<ChatEntity>

    /** Resolves [chat]'s `branchedFromMessageId` (a message id, not a chat id — see [forkChat]) to
     * the id of the chat that message actually belongs to. Null if [chat] was never forked, or if
     * its source chat has since been deleted (the message id is now dangling — the fork survives
     * as an orphaned root rather than erroring). */
    suspend fun getParentChatId(chat: ChatEntity): String?

    suspend fun upsert(chat: ChatEntity)

    suspend fun delete(chat: ChatEntity)
    
    suspend fun deleteChatWithMessages(chat: ChatEntity)
}
