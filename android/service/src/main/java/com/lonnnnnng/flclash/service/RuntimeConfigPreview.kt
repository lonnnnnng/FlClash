package com.lonnnnnng.flclash.service

data class RuntimeConfigPreview(
    val profileId: Long,
    val label: String,
    val content: String,
    val isApplied: Boolean,
)

interface RuntimeConfigPreviewProvider {
    suspend fun previewConfig(profileId: Long): RuntimeConfigPreview
}
