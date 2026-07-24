package com.radiopolska.data

data class RadioStation(
    val id: String,
    val name: String,
    val frequency: String,
    val city: String,
    val region: String?,
    val category: String,
    val genre: String,
    val streamUrl: String,
    val fallbackStreamUrl: String?,
    val streamUrls: List<String>,
    val bitrate: Int,
    val description: String,
    val iconUrl: String,
    val accentColor: String,
    val isHighQuality: Boolean,
)
