package com.tapedeck.dsp

import androidx.documentfile.provider.DocumentFile

/**
 * Resolves M3U entries against a picked SAF folder tree. There is no
 * filesystem path to open under scoped storage, so entries are matched by
 * walking DocumentFile children - first by following the entry's path
 * segments relative to the tree root, then (for absolute paths pointing
 * somewhere else on disk, or path separators that don't match the tree
 * layout) by a shallow filename search under that same root.
 */
data class PlaylistFileEntry(val file: DocumentFile, val relativePath: String)

object PlaylistResolver {

    private val playlistExtensions = setOf("m3u", "m3u8")

    /** Recursively collects every playlist file under [root], each tagged with its path relative to [root]. */
    fun findAllPlaylistFiles(root: DocumentFile, maxDepth: Int = 4): List<PlaylistFileEntry> {
        val results = mutableListOf<PlaylistFileEntry>()

        fun walk(dir: DocumentFile, path: String, depth: Int) {
            if (depth > maxDepth) return
            for (child in dir.listFiles()) {
                val name = child.name ?: continue
                val childPath = if (path.isEmpty()) name else "$path/$name"
                val extension = name.substringAfterLast('.', "").lowercase()
                if (child.isFile && extension in playlistExtensions) {
                    results.add(PlaylistFileEntry(child, childPath))
                } else if (child.isDirectory) {
                    walk(child, childPath, depth + 1)
                }
            }
        }

        walk(root, "", 0)
        return results
    }

    fun resolveTrack(root: DocumentFile, rawPath: String): DocumentFile? {
        val normalized = rawPath.replace('\\', '/').trim()
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) return null

        val segments = normalized.trimStart('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        var current: DocumentFile? = root
        for (segment in segments) {
            current = current?.findFile(segment)
            if (current == null) break
        }
        if (current != null && current.isFile) return current

        return findByName(root, segments.last(), maxDepth = 3)
    }

    private fun findByName(dir: DocumentFile, name: String, maxDepth: Int): DocumentFile? {
        if (maxDepth < 0) return null
        val children = dir.listFiles()
        for (child in children) {
            if (child.isFile && child.name == name) return child
        }
        for (child in children) {
            if (child.isDirectory) {
                findByName(child, name, maxDepth - 1)?.let { return it }
            }
        }
        return null
    }
}
