package com.auralis.music.ui.screens.player

import com.auralis.music.util.ImageUtils

fun getHighResThumbnail(url: String?): String? {
    return ImageUtils.getHighResThumbnailUrl(url)
}

fun formatDuration(millis: Long): String = com.auralis.music.util.TimeUtil.formatPosition(millis)

