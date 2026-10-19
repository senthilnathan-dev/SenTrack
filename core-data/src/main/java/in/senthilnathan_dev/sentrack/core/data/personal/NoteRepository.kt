package `in`.senthilnathan_dev.sentrack.core.data.personal

import `in`.senthilnathan_dev.sentrack.core.data.DatabaseHolder
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Notes, folders, tags and attachment records. Screens go through this, never straight to Room.
 * Call the `observe` functions from composition, as with the other repositories.
 */
class NoteRepository(private val holder: DatabaseHolder) {
    private fun dao() = holder.get().noteDao()

    fun notes(): Flow<List<NoteEntity>> = dao().observeNotes()
    fun folders(): Flow<List<NoteFolderEntity>> = dao().observeFolders()
    fun tags(): Flow<List<NoteTagEntity>> = dao().observeTags()
    fun tagLinks(): Flow<List<NoteTagLinkEntity>> = dao().observeTagLinks()
    fun attachments(): Flow<List<NoteAttachmentEntity>> = dao().observeAttachments()

    suspend fun note(id: String): NoteEntity? = dao().note(id)

    /** Saves the note and sets its tags to exactly [tagNames], creating tags that do not exist yet. */
    suspend fun saveNote(note: NoteEntity, tagNames: Set<String>) {
        val dao = dao()
        dao.upsertNote(note)
        val ids = tagNames.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.map { name ->
            dao.tagByName(name)?.id ?: NoteTagEntity(UUID.randomUUID().toString(), name).also { dao.upsertTag(it) }.id
        }
        dao.clearLinks(note.id)
        dao.upsertLinks(ids.map { NoteTagLinkEntity(note.id, it) })
    }

    /**
     * Permanently deletes a note with its tag links and attachment records. Returns the vault ids of its files so the
     * caller can remove the encrypted files too.
     */
    suspend fun deleteNote(id: String): List<String> {
        val dao = dao()
        val files = dao.attachmentsFor(id).map { it.vaultFileId }
        dao.deleteAttachmentsFor(id)
        dao.clearLinks(id)
        dao.deleteNote(id)
        return files
    }

    suspend fun addFolder(name: String): NoteFolderEntity {
        val dao = dao()
        val folder = NoteFolderEntity(UUID.randomUUID().toString(), name.trim(), false, dao.maxFolderOrder() + 1, System.currentTimeMillis())
        dao.upsertFolder(folder)
        return folder
    }

    suspend fun saveFolder(folder: NoteFolderEntity) = dao().upsertFolder(folder)

    suspend fun saveAttachment(attachment: NoteAttachmentEntity) = dao().upsertAttachment(attachment)

    suspend fun deleteAttachment(id: String) = dao().deleteAttachment(id)
}
