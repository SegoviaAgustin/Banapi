package app.banapi.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observeChat(id: Long): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChat(id: Long): ChatEntity?

    @Query("SELECT COUNT(*) FROM chats")
    fun observeChatCount(): Flow<Int>

    @Query("SELECT id FROM chats ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestChatId(): Long?

    @Insert
    suspend fun insertChat(chat: ChatEntity): Long

    @Query("UPDATE chats SET updatedAt = :time WHERE id = :id")
    suspend fun touchChat(id: Long, time: Long)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("UPDATE chats SET title = :title WHERE id = :id")
    suspend fun renameChat(id: Long, title: String)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteChat(id: Long)

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()

    /** Chat donde se generó una imagen de la galería. */
    @Query(
        "SELECT m.chatId FROM message_images i JOIN messages m ON m.id = i.messageId " +
            "WHERE i.path = :path AND i.origin = 'Generated' LIMIT 1",
    )
    suspend fun chatIdForImage(path: String): Long?

    @Query(
        "SELECT i.* FROM message_images i JOIN messages m ON m.id = i.messageId WHERE m.chatId = :chatId",
    )
    suspend fun imagesOfChat(chatId: Long): List<MessageImageEntity>

    @Transaction
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt, id")
    fun observeMessages(chatId: Long): Flow<List<MessageWithImages>>

    @Transaction
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt, id")
    suspend fun getMessages(chatId: Long): List<MessageWithImages>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessage(id: Long): MessageEntity?

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Insert
    suspend fun insertImages(images: List<MessageImageEntity>)

    @Query("SELECT path FROM message_images")
    suspend fun allImagePaths(): List<String>

    @Insert
    suspend fun insertUsage(usage: UsageEntity)

    @Query(
        "SELECT COALESCE(SUM(costMicros), 0) AS costMicros, COALESCE(SUM(imageCount), 0) AS images, " +
            "COUNT(*) AS requests FROM usage WHERE createdAt >= :since",
    )
    fun observeUsage(since: Long): Flow<UsageSummary>

    /** Las generaciones que quedaron a medias cuando se cerró la app. */
    @Query("UPDATE messages SET status = 'Failed', error = 'Interrupted' WHERE status = 'Pending'")
    suspend fun failPendingMessages()
}
