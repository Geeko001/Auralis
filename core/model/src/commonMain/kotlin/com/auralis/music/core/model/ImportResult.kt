package com.auralis.music.core.model

data class ImportResult(
    val originalTitle: String,
    val originalArtist: String,
    val matchedSong: Song?
)
