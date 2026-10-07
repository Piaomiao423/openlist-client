package com.openlist.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openlist.client.data.model.FileItem
import com.openlist.client.data.repository.OpenListRepository
import com.openlist.client.data.repository.UnauthorizedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class FileListUiState(
    val currentPath: String = "/",
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchMode: Boolean = false,
    val searchKeyword: String = ""
)

class FileListViewModel(
    private val repository: OpenListRepository,
    initialPath: String = "/"
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileListUiState())
    val uiState: StateFlow<FileListUiState> = _uiState

    private val pathStack = ArrayDeque<String>()

    /** 各目录的滚动位置记忆：path -> (firstVisibleItemIndex, scrollOffset)，返回上级时恢复 */
    private val scrollMemory = HashMap<String, Pair<Int, Int>>()

    /** 记录指定目录的滚动位置 */
    fun saveScroll(path: String, index: Int, offset: Int) {
        scrollMemory[path] = index to offset
    }

    /** 取回指定目录的滚动位置（无记录返回 null） */
    fun restoreScroll(path: String): Pair<Int, Int>? = scrollMemory[path]

    init {
        loadDirectory(initialPath)
    }

    fun loadDirectory(path: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, searchMode = false)
        viewModelScope.launch {
            repository.listFiles(path).fold(
                onSuccess = { items ->
                    val sorted = sortItems(items)
                    _uiState.value = _uiState.value.copy(
                        currentPath = path,
                        items = sorted,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    val msg = if (e is UnauthorizedException) "登录已过期，请重新登录"
                    else e.message ?: "加载失败"
                    _uiState.value = _uiState.value.copy(isLoading = false, error = msg)
                }
            )
        }
    }

    fun openDirectory(item: FileItem) {
        if (!item.isDir) return
        val current = _uiState.value.currentPath
        val newPath = if (current == "/") "/${item.name}" else "$current/${item.name}"
        pathStack.addLast(current)
        loadDirectory(newPath)
    }

    fun goBack(): Boolean {
        if (pathStack.isEmpty()) return false
        val previous = pathStack.removeLast()
        loadDirectory(previous)
        return true
    }

    fun refresh() {
        loadDirectory(_uiState.value.currentPath)
    }

    fun getDownloadUrl(path: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getDownloadUrl(path).getOrNull())
        }
    }

    // ===== 搜索 =====

    fun search(keywords: String) {
        val kw = keywords.trim()
        if (kw.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            searchMode = true,
            searchKeyword = kw
        )
        viewModelScope.launch {
            repository.search(kw).fold(
                onSuccess = { items ->
                    _uiState.value = _uiState.value.copy(
                        items = sortItems(items),
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    val msg = if (e is UnauthorizedException) "登录已过期，请重新登录"
                    else e.message ?: "搜索失败"
                    _uiState.value = _uiState.value.copy(isLoading = false, error = msg)
                }
            )
        }
    }

    fun exitSearch() {
        _uiState.value = _uiState.value.copy(searchMode = false, searchKeyword = "")
        loadDirectory(_uiState.value.currentPath)
    }

    // ===== 文件管理 =====

    fun createFolder(name: String, onDone: (Boolean, String?) -> Unit) {
        val path = childPath(name)
        viewModelScope.launch {
            repository.createDirectory(path).fold(
                onSuccess = { onDone(true, null); refresh() },
                onFailure = { e -> onDone(false, e.message ?: "创建失败") }
            )
        }
    }

    fun renameItem(item: FileItem, newName: String, onDone: (Boolean, String?) -> Unit) {
        val path = absolutePath(item)
        viewModelScope.launch {
            repository.rename(path, newName).fold(
                onSuccess = { onDone(true, null); refresh() },
                onFailure = { e -> onDone(false, e.message ?: "重命名失败") }
            )
        }
    }

    fun deleteItem(item: FileItem, onDone: (Boolean, String?) -> Unit) {
        val dir = _uiState.value.currentPath
        viewModelScope.launch {
            repository.deleteFiles(dir, listOf(item.name)).fold(
                onSuccess = { onDone(true, null); refresh() },
                onFailure = { e -> onDone(false, e.message ?: "删除失败") }
            )
        }
    }

    fun uploadFile(localFile: File, onDone: (Boolean, String?) -> Unit) {
        val dir = _uiState.value.currentPath
        viewModelScope.launch {
            repository.uploadFile(localFile, dir).fold(
                onSuccess = { onDone(true, null); refresh() },
                onFailure = { e -> onDone(false, e.message ?: "上传失败") }
            )
        }
    }

    // ===== 工具 =====

    private fun sortItems(items: List<FileItem>): List<FileItem> =
        items.sortedWith(
            compareByDescending<FileItem> { it.isDir }
                .thenBy { it.name.lowercase() }
        )

    private fun absolutePath(item: FileItem): String {
        val current = _uiState.value.currentPath
        return if (current == "/") "/${item.name}" else "$current/${item.name}"
    }

    private fun childPath(name: String): String {
        val current = _uiState.value.currentPath
        return if (current == "/") "/$name" else "$current/$name"
    }
}
