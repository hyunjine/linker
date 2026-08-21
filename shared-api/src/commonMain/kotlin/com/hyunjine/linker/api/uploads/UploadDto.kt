package com.hyunjine.linker.api.uploads

import kotlinx.serialization.Serializable

/** `docs/api-design.md` §4.5 `POST /uploads/profile-image` 응답. */
@Serializable
data class UploadImageResponse(
    val url: String,
    val byteSize: Long,
    val mimeType: String,
)
