package com.kitsune.feature.chat.branchtree

import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatMode
import com.kitsune.core.data.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildChatBranchTreeUseCaseTest {

    private val chatRepository = mockk<ChatRepository>()
    private val useCase = BuildChatBranchTreeUseCase(chatRepository)

    private fun chat(id: String, branchedFromMessageId: String? = null, createdAt: Long = 0L) = ChatEntity(
        id = id,
        universeId = null,
        personaId = "persona-1",
        title = id,
        mode = ChatMode.CHAT,
        branchedFromMessageId = branchedFromMessageId,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    @Test
    fun `returns empty when the chat itself no longer exists`() = runTest {
        coEvery { chatRepository.getBranchFamilyChats("missing") } returns emptyList()

        val result = useCase("missing")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a single never-forked chat is its own one-node root`() = runTest {
        val solo = chat("solo")
        coEvery { chatRepository.getBranchFamilyChats("solo") } returns listOf(solo)
        coEvery { chatRepository.getParentChatId(solo) } returns null

        val result = useCase("solo")

        assertEquals(1, result.size)
        assertEquals("solo", result.single().chat.id)
        assertTrue(result.single().children.isEmpty())
    }

    @Test
    fun `nests a direct fork under its parent`() = runTest {
        val root = chat("root", createdAt = 1L)
        val fork = chat("fork", branchedFromMessageId = "msg-in-root", createdAt = 2L)
        coEvery { chatRepository.getBranchFamilyChats("fork") } returns listOf(root, fork)
        coEvery { chatRepository.getParentChatId(root) } returns null
        coEvery { chatRepository.getParentChatId(fork) } returns "root"

        val result = useCase("fork")

        assertEquals(1, result.size)
        val rootNode = result.single()
        assertEquals("root", rootNode.chat.id)
        assertEquals(1, rootNode.children.size)
        assertEquals("fork", rootNode.children.single().chat.id)
    }

    @Test
    fun `nests a branch-of-a-branch two levels deep`() = runTest {
        val root = chat("root", createdAt = 1L)
        val fork1 = chat("fork1", branchedFromMessageId = "msg-in-root", createdAt = 2L)
        val fork2 = chat("fork2", branchedFromMessageId = "msg-in-fork1", createdAt = 3L)
        coEvery { chatRepository.getBranchFamilyChats("fork2") } returns listOf(root, fork1, fork2)
        coEvery { chatRepository.getParentChatId(root) } returns null
        coEvery { chatRepository.getParentChatId(fork1) } returns "root"
        coEvery { chatRepository.getParentChatId(fork2) } returns "fork1"

        val result = useCase("fork2")

        val rootNode = result.single()
        val fork1Node = rootNode.children.single()
        assertEquals("fork1", fork1Node.chat.id)
        assertEquals("fork2", fork1Node.children.single().chat.id)
    }

    @Test
    fun `two chats for the same persona that were never forked from each other are two separate roots`() = runTest {
        val a = chat("a", createdAt = 1L)
        val b = chat("b", createdAt = 2L)
        coEvery { chatRepository.getBranchFamilyChats("a") } returns listOf(a, b)
        coEvery { chatRepository.getParentChatId(a) } returns null
        coEvery { chatRepository.getParentChatId(b) } returns null

        val result = useCase("a")

        assertEquals(2, result.size)
        assertEquals(listOf("a", "b"), result.map { it.chat.id })
    }

    @Test
    fun `a fork whose source chat was deleted surfaces as its own orphaned root rather than being dropped`() = runTest {
        val orphan = chat("orphan", branchedFromMessageId = "msg-in-deleted-chat", createdAt = 1L)
        coEvery { chatRepository.getBranchFamilyChats("orphan") } returns listOf(orphan)
        // The source chat's messages cascade-deleted with it, so the message id no longer
        // resolves to anything — getParentChatId legitimately returns null here (see its own
        // doc comment), not the id of a chat that isn't even in this family.
        coEvery { chatRepository.getParentChatId(orphan) } returns null

        val result = useCase("orphan")

        assertEquals(1, result.size)
        assertEquals("orphan", result.single().chat.id)
        assertEquals("msg-in-deleted-chat", result.single().chat.branchedFromMessageId)
    }
}
