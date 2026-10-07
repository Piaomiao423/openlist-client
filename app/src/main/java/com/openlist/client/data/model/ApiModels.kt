package com.openlist.client.data.model

import com.google.gson.annotations.SerializedName

// ===== 通用响应 =====
data class ApiResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: T?
)

// ===== 登录 =====
data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("token") val token: String
)

// ===== 文件列表 =====
data class FileListRequest(
    @SerializedName("path") val path: String,
    @SerializedName("password") val password: String = "",
    @SerializedName("page") val page: Int = 1,
    @SerializedName("per_page") val perPage: Int = 0,
    @SerializedName("refresh") val refresh: Boolean = false
)

data class FileListData(
    @SerializedName("content") val content: List<FileItem>?,
    @SerializedName("total") val total: Long?,
    @SerializedName("readme") val readme: String?
)

// ===== 文件信息 =====
data class FileGetRequest(
    @SerializedName("path") val path: String,
    @SerializedName("password") val password: String = ""
)

data class FileGetData(
    @SerializedName("raw_url") val rawUrl: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("size") val size: Long?,
    @SerializedName("header") val header: String?
)

// ===== 文件直链（AList/OpenList 标准下载方式）=====
data class FileLinkRequest(
    @SerializedName("path") val path: String,
    @SerializedName("password") val password: String = ""
)

data class FileLinkData(
    @SerializedName("url") val url: String?,
    @SerializedName("header") val header: String?,
    @SerializedName("expire") val expire: Long?
)

// ===== 创建目录 =====
data class MkdirRequest(
    @SerializedName("path") val path: String
)

// ===== 上传任务（OpenList 异步任务队列）=====
data class UploadData(
    @SerializedName("task") val task: UploadTask?
)

data class UploadTask(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("progress") val progress: Int?,
    @SerializedName("total_bytes") val totalBytes: Long?,
    @SerializedName("error") val error: String?
)

data class TaskInfo(
    @SerializedName("id") val id: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("progress") val progress: Float?,
    @SerializedName("total_bytes") val totalBytes: Long?,
    @SerializedName("error") val error: String?
)

// ===== 删除 / 重命名 =====
data class RemoveRequest(
    @SerializedName("dir") val dir: String,
    @SerializedName("names") val names: List<String>
)

data class RenameRequest(
    @SerializedName("path") val path: String,
    @SerializedName("name") val newName: String
)

// ===== 搜索 =====
data class SearchRequest(
    @SerializedName("parent") val parent: String,
    @SerializedName("keywords") val keywords: String,
    @SerializedName("scope") val scope: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("per_page") val perPage: Int = 0,
    @SerializedName("password") val password: String = ""
)
