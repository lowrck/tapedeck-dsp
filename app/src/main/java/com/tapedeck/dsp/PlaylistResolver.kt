package com.tapedeck.dsp

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

data class PlaylistFileEntry(val file: DocumentFile, val relativePath: String, val name: String)

/** A file discovered while indexing a SAF tree, with its name/path cached from the directory query. */
data class IndexedFile(val file: DocumentFile, val name: String, val relativePath: String)

/**
 * A one-pass index of every file under a picked SAF folder tree, keyed by
 * both full relative path and bare filename so playlist-track resolution is
 * an O(1) map lookup afterward instead of a fresh tree walk per track.
 */
class SafFileIndex internal constructor(
    val allFiles: List<IndexedFile>,
    private val byRelativePath: Map<String, IndexedFile>,
    private val byName: Map<String, List<IndexedFile>>,
) {
    fun resolve(rawPath: String): IndexedFile? {
        val normalized = rawPath.replace('\\', '/').trim()
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) return null

        val segments = normalized.trimStart('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        byRelativePath[segments.joinToString("/")]?.let { return it }
        return byName[segments.last()]?.firstOrNull()
    }
}

/**
 * Resolves M3U entries (and audio files generally) against a picked SAF
 * folder tree. There is no filesystem path to open under scoped storage, so
 * everything is matched by walking DocumentFile children.
 *
 * [DocumentFile.listFiles] issues one binder query per directory, but every
 * subsequent property access on a child (.name, .isFile, .isDirectory) is
 * its OWN separate binder round trip - for a folder of a few hundred files
 * that's thousands of IPC calls. [buildIndex] avoids that by querying each
 * directory once via [DocumentsContract] with a full projection (document
 * id, display name, mime type) and caching the results, so both playlist
 * lookups and library scans only pay tree-walk cost once, not once per file
 * resolved.
 */
object PlaylistResolver {

    private val playlistExtensions = setOf("m3u", "m3u8")

    fun buildIndex(context: Context, root: DocumentFile, maxDepth: Int = 8): SafFileIndex {
        val all = mutableListOf<IndexedFile>()
        val byPath = HashMap<String, IndexedFile>()
        val byName = HashMap<String, MutableList<IndexedFile>>()
        val resolver = context.contentResolver

        fun walk(dirUri: Uri, path: String, depth: Int) {
            if (depth > maxDepth) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                dirUri,
                DocumentsContract.getDocumentId(dirUri),
            )

            val children = mutableListOf<Triple<String, String, Boolean>>()
            try {
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: continue
                        val isDir = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                        children.add(Triple(documentId, name, isDir))
                    }
                }
            } catch (t: Throwable) {
                return
            }

            for ((documentId, name, isDir) in children) {
                val childUri = DocumentsContract.buildDocumentUriUsingTree(dirUri, documentId)
                val childPath = if (path.isEmpty()) name else "$path/$name"
                if (isDir) {
                    walk(childUri, childPath, depth + 1)
                } else {
                    val docFile = DocumentFile.fromSingleUri(context, childUri) ?: continue
                    val indexed = IndexedFile(docFile, name, childPath)
                    all.add(indexed)
                    byPath[childPath] = indexed
                    byName.getOrPut(name) { mutableListOf() }.add(indexed)
                }
            }
        }

        walk(root.uri, "", 0)
        return SafFileIndex(all, byPath, byName)
    }

    fun findAllPlaylistFiles(index: SafFileIndex): List<PlaylistFileEntry> =
        index.allFiles
            .filter { it.name.substringAfterLast('.', "").lowercase() in playlistExtensions }
            .map { PlaylistFileEntry(it.file, it.relativePath, it.name) }

    fun resolveTrack(index: SafFileIndex, rawPath: String): IndexedFile? = index.resolve(rawPath)
}
