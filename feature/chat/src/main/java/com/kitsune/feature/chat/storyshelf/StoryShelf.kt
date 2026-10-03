package com.kitsune.feature.chat.storyshelf

import com.kitsune.core.data.local.entities.ChatEntity

/**
 * One story as it appears on the shelf.
 *
 * The shelf shows **stories**, not conversations. That distinction is the whole repositioning: a list
 * of chats is organised around what the database happens to store, a shelf is organised around what
 * the user is living. Everything here is what belongs on a spine — a title, a cover, how far along it
 * is, and whether something moved since they last looked.
 */
data class ShelfStory(
    val chatId: String,
    val title: String,
    val coverImageId: String?,
    /** Cast portrait to fall back on when there is no cover yet. */
    val avatarImageId: String?,
    val chapterCount: Int,
    val isEnsemble: Boolean,
    val hasUnseenDevelopment: Boolean,
    val updatedAt: Long
)

/**
 * Whether this story has moved since the user last opened it.
 *
 * The comparison is against `lastVisitedAt` and **not** `updatedAt` alone, because `updatedAt` moves
 * for reasons that have nothing to do with the reader: a background summarization pass, a lore
 * extraction, a chapter closing. Those are exactly the moments something interesting happened, which
 * is why the badge is worth having — but only relative to when the user actually looked.
 *
 * `lastVisitedAt == 0` means "never seen since we started counting", which every story that predates
 * the field carries. Those get **no** badge: on the first launch after the migration, lighting up
 * every story the user owns would say nothing at all.
 */
fun hasUnseenDevelopment(chat: ChatEntity): Boolean =
    chat.lastVisitedAt > 0L && chat.updatedAt > chat.lastVisitedAt

/**
 * Shelf order: what moved without you, then what you touched most recently.
 *
 * Stories with news come first because that is the shelf's reason to exist — otherwise a story that
 * developed three weeks ago stays buried under conversations the user opened yesterday and closed
 * immediately. Within each group, most recent first, which is the order every chat list already uses
 * and the one people expect.
 */
fun shelfOrder(stories: List<ShelfStory>): List<ShelfStory> = stories.sortedWith(
    compareByDescending<ShelfStory> { it.hasUnseenDevelopment }.thenByDescending { it.updatedAt }
)

/**
 * The title to show for a story that has never been named.
 *
 * `ChatEntity.title` is empty for every persona chat — `createChat` passes `""` and nothing has ever
 * filled it. The chaptering pass already asks the model for an evocative chapter title, so the first
 * closed chapter names the story; before that, the character's name is a better spine than "Chat 4",
 * and an ensemble scene falls back to a generic label supplied by the caller.
 */
fun shelfTitle(chat: ChatEntity, firstChapterTitle: String?, castName: String?, fallback: String): String =
    chat.title.ifBlank { firstChapterTitle.orEmpty() }
        .ifBlank { castName.orEmpty() }
        .ifBlank { fallback }
