package com.tapedeck.dsp

data class PlaylistEntry(val title: String?, val path: String)

/** Minimal M3U/M3U8 parser: pairs #EXTINF metadata with the path line that follows it. */
object PlaylistParser {

    private const val BOM = "﻿"

    fun parse(content: String): List<PlaylistEntry> {
        val entries = mutableListOf<PlaylistEntry>()
        var pendingTitle: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().removePrefix(BOM)
            if (line.isEmpty()) return@forEach

            if (line.startsWith("#EXTINF:")) {
                val comma = line.indexOf(',')
                pendingTitle = if (comma in 0 until line.length - 1) {
                    line.substring(comma + 1).trim().ifBlank { null }
                } else {
                    null
                }
            } else if (line.startsWith("#")) {
                // Other directive/comment - ignored.
            } else {
                entries.add(PlaylistEntry(title = pendingTitle, path = line))
                pendingTitle = null
            }
        }

        return entries
    }
}
