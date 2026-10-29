package app.banapi.data.chat

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.banapi.data.GenerationError

enum class ResponseMode { ImagesOnly, ImagesAndText }

enum class Role { User, Model }

enum class MessageStatus { Pending, Done, Failed }

enum class ImageOrigin { Attachment, Generated, Marked }

/** Opciones de generación de un chat. null = Auto (decide el modelo). */
data class ChatOptions(
    val responseMode: ResponseMode = ResponseMode.ImagesOnly,
    val aspectRatio: String? = null,
    val imageSize: String? = null,
    val thinkingLevel: String? = null,
    val googleSearch: Boolean = false,
    val instructions: String = "",
) {
    val isDefault: Boolean get() = this == ChatOptions()

    companion object {
        // Valores que acepta Nano Banana 2.1.
        val ASPECT_RATIOS = listOf(
            "1:1", "2:3", "3:2", "3:4", "4:3", "4:5", "5:4", "9:16", "16:9", "21:9", "1:4", "4:1", "1:8", "8:1",
        )
        const val SIZE_2K = "2K" // Auto = 1K
        const val SIZE_4K = "4K"
        const val THINKING_MINIMAL = "minimal" // Auto = medium
        const val THINKING_HIGH = "high"
    }
}

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    @Embedded val options: ChatOptions = ChatOptions(),
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chatId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val role: Role,
    val status: MessageStatus,
    val text: String = "",
    val error: GenerationError? = null,
    val errorDetail: String? = null,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val createdAt: Long,
    /** Costo estimado en millonésimas de dólar. */
    @ColumnInfo(defaultValue = "0") val costMicros: Long = 0,
)

/** Un pedido a la API. Se guarda aparte para que el gasto no se pierda al borrar chats. */
@Entity(tableName = "usage", indices = [Index("createdAt")])
data class UsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val imageCount: Int,
    val costMicros: Long,
)

data class UsageSummary(val costMicros: Long, val images: Int, val requests: Int)

/** Imagen de un mensaje. La ruta es relativa a la carpeta privada de la app. */
@Entity(
    tableName = "message_images",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId"), Index("path")],
)
data class MessageImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val path: String,
    val origin: ImageOrigin,
    val width: Int,
    val height: Int,
    val position: Int,
)

data class MessageWithImages(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val images: List<MessageImageEntity>,
)
