package com.kitsune.feature.chat.branchtree

import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.repository.ChatRepository
import javax.inject.Inject

/** One chat in a branch family, with its already-resolved forks nested underneath — assembled
 * client-side since [ChatEntity] only stores `branchedFromMessageId` (a message id in the PARENT
 * chat, not a parent chat id directly; see [ChatRepository.getParentChatId]). */
data class BranchTreeNode(val chat: ChatEntity, val children: List<BranchTreeNode>)

/**
 * Builds the full branch-family forest for whichever chat [chatId] belongs to — every chat sharing
 * its persona (or universe, for an ensemble chat), arranged into parent/child trees by walking each
 * chat's `branchedFromMessageId`. Usually a single tree, but a persona can have several entirely
 * independent chats that were never forked from one another ([ChatRepository.createChat] called
 * more than once) — each such chat is its own root, hence a forest (list of trees) rather than one
 * tree. A fork whose source chat was since deleted also surfaces as its own root rather than being
 * dropped, since its `branchedFromMessageId` no longer resolves to anything in this family.
 */
class BuildChatBranchTreeUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String): List<BranchTreeNode> {
        val familyChats = chatRepository.getBranchFamilyChats(chatId)
        if (familyChats.isEmpty()) return emptyList()

        val familyIds = familyChats.map { it.id }.toSet()
        val parentIdByChatId = familyChats.associate { chat -> chat.id to chatRepository.getParentChatId(chat) }
        val childrenByParentId = familyChats.groupBy { parentIdByChatId[it.id] }

        fun buildNode(chat: ChatEntity): BranchTreeNode = BranchTreeNode(
            chat = chat,
            children = childrenByParentId[chat.id].orEmpty().sortedBy { it.createdAt }.map(::buildNode)
        )

        val roots = familyChats.filter { chat ->
            val parentId = parentIdByChatId[chat.id]
            parentId == null || parentId !in familyIds
        }
        return roots.sortedBy { it.createdAt }.map(::buildNode)
    }
}
