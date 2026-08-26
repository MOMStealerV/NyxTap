package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.EmailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {
    @Query("SELECT * FROM emails ORDER BY timestamp DESC")
    fun getAllEmails(): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE mailbox = :mailbox ORDER BY timestamp DESC")
    fun getEmailsForMailbox(mailbox: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE id = :id LIMIT 1")
    suspend fun getEmailById(id: String): EmailEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmail(email: EmailEntity)

    @Query("UPDATE emails SET isCopied = 1 WHERE id = :id")
    suspend fun markAsCopied(id: String)

    @Query("DELETE FROM emails")
    suspend fun clearAll()
}
