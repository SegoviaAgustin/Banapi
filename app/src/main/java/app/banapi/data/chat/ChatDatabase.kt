package app.banapi.data.chat

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Chats y mensajes, guardados solo en el teléfono. */
@Database(
    entities = [ChatEntity::class, MessageEntity::class, MessageImageEntity::class, UsageEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        fun create(context: Context): ChatDatabase =
            Room.databaseBuilder(context, ChatDatabase::class.java, "chats.db").build()
    }
}
