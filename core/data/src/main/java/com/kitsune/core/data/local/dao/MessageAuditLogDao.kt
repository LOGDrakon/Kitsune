package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitsune.core.data.local.entities.MessageAuditLogEntity

/** Append-only by construction — no `@Update`/`@Delete` method is exposed here on purpose, see
 * [MessageAuditLogEntity]'s doc comment. */
@Dao
interface MessageAuditLogDao {

    @Query("SELECT * FROM message_audit_log WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getByChatId(chatId: String): List<MessageAuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: MessageAuditLogEntity)
}
