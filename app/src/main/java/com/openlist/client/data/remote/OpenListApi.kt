package com.openlist.client.data.remote

import com.openlist.client.data.model.*
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface OpenListApi {

    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

    @POST("/api/fs/list")
    suspend fun listFiles(
        @Body request: FileListRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse<FileListData>>

    @POST("/api/fs/get")
    suspend fun getFile(
        @Body request: FileGetRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse<FileGetData>>

    /** 获取文件直链（返回真实下载地址 + 需携带的请求头） */
    @POST("/api/fs/link")
    suspend fun linkFile(
        @Body request: FileLinkRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse<FileLinkData>>

    @POST("/api/fs/mkdir")
    suspend fun mkdir(
        @Body request: MkdirRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse<Unit>>

    @POST("/api/fs/remove")
    suspend fun remove(
        @Body request: RemoveRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse<Unit>>

    @POST("/api/fs/rename")
    suspend fun rename(
        @Body request: RenameRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse<Unit>>

    @POST("/api/search")
    suspend fun search(
        @Body request: SearchRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse<FileListData>>

    /**
     * 上传（AList/OpenList 标准方式）：目标路径通过 File-Path 请求头传递（URL 编码的完整路径），
     * body 为原始字节流。返回体可能含异步任务（data.task），需轮询确认最终结果。
     */
    @PUT("/api/fs/put")
    suspend fun uploadFile(
        @Header("File-Path") filePath: String,
        @Header("Authorization") token: String,
        @Header("As-Task") asTask: String = "false",
        @Header("Content-Type") contentType: String = "application/octet-stream",
        @Body body: RequestBody
    ): Response<ApiResponse<UploadData>>

    /** 上传（旧版兼容：目标路径通过 query 传递） */
    @PUT("/api/fs/put")
    suspend fun uploadFileWithQuery(
        @Query("path") path: String,
        @Header("Authorization") token: String,
        @Header("As-Task") asTask: String = "false",
        @Header("Content-Type") contentType: String = "application/octet-stream",
        @Body body: RequestBody
    ): Response<ApiResponse<UploadData>>

    /** 查询上传任务状态（AList/OpenList 任务系统） */
    @POST("/api/task/upload/info")
    suspend fun taskInfo(
        @Query("tid") tid: String,
        @Header("Authorization") token: String
    ): Response<ApiResponse<List<TaskInfo>>>
}
