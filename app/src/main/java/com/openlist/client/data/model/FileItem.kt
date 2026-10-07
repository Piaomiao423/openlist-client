package com.openlist.client.data.model

import com.google.gson.annotations.SerializedName
import java.util.Locale

data class FileItem(
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("is_dir") val isDir: Boolean,
    @SerializedName("modified") val modified: String,
    @SerializedName("created") val created: String?,
    @SerializedName("sign") val sign: String?,
    @SerializedName("thumb") val thumb: String?,
    @SerializedName("type") val type: Int,
    @SerializedName("hashinfo") val hashInfo: String?
) {
    val displaySize: String
        get() = when {
            isDir -> ""
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
            size < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024))
            else -> String.format(Locale.US, "%.2f GB", size / (1024.0 * 1024 * 1024))
        }

    val fileExtension: String
        get() = name.substringAfterLast('.', "").lowercase(Locale.US)
}
