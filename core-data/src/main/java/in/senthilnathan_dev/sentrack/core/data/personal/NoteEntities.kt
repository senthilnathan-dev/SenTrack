package `in`.senthilnathan_dev.sentrack.core.data.personal

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A folder for grouping notes. Archived folders are hidden but keep their notes. */
@Entity(tableName = "note_folder")
data class NoteFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAtMillis: Long,
)

/**
 * A note. [body] is lightweight Markdown (headings, bold, italic, lists, check lists, quotes) written with the
 * editor toolbar. Notes are archived (hidden), not deleted, unless the user confirms a permanent delete.
 */
@Entity(tableName = "note", indices = [Index("folderId"), Index("updatedAtMillis")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val title: String,
    val body: String,
    val pinned: Boolean,
    val archived: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(tableName = "note_tag", indices = [Index(value = ["name"], unique = true)])
data class NoteTagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(tableName = "note_tag_link", primaryKeys = ["noteId", "tagId"], indices = [Index("tagId")])
data class NoteTagLinkEntity(
    val noteId: String,
    val tagId: String,
)

/** A file attached to a note. The file itself is encrypted in the file vault under [vaultFileId]. */
@Entity(tableName = "note_attachment", indices = [Index("noteId")])
data class NoteAttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val vaultFileId: String,
    val addedAtMillis: Long,
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM note ORDER BY pinned DESC, updatedAtMillis DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM note_folder ORDER BY archived, sortOrder, name COLLATE NOCASE")
    fun observeFolders(): Flow<List<NoteFolderEntity>>

    @Query("SELECT * FROM note_tag ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<NoteTagEntity>>

    @Query("SELECT * FROM note_tag_link")
    fun observeTagLinks(): Flow<List<NoteTagLinkEntity>>

    @Query("SELECT * FROM note_attachment ORDER BY addedAtMillis")
    fun observeAttachments(): Flow<List<NoteAttachmentEntity>>

    @Query("SELECT * FROM note WHERE id = :id")
    suspend fun note(id: String): NoteEntity?

    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Query("DELETE FROM note WHERE id = :id")
    suspend fun deleteNote(id: String)

    @Upsert
    suspend fun upsertFolder(folder: NoteFolderEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM note_folder")
    suspend fun maxFolderOrder(): Int

    @Query("SELECT * FROM note_tag WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun tagByName(name: String): NoteTagEntity?

    @Upsert
    suspend fun upsertTag(tag: NoteTagEntity)

    @Query("DELETE FROM note_tag_link WHERE noteId = :noteId")
    suspend fun clearLinks(noteId: String)

    @Upsert
    suspend fun upsertLinks(links: List<NoteTagLinkEntity>)

    @Upsert
    suspend fun upsertAttachment(attachment: NoteAttachmentEntity)

    @Query("DELETE FROM note_attachment WHERE id = :id")
    suspend fun deleteAttachment(id: String)

    @Query("SELECT * FROM note_attachment WHERE noteId = :noteId")
    suspend fun attachmentsFor(noteId: String): List<NoteAttachmentEntity>

    @Query("DELETE FROM note_attachment WHERE noteId = :noteId")
    suspend fun deleteAttachmentsFor(noteId: String)
}
