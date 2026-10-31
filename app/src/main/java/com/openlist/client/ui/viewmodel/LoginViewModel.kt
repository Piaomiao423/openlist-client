package com.openlist.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openlist.client.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

class LoginViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun login(serverUrl: String, username: String, password: String, onSuccess: () -> Unit) {
        if (_uiState.value.isLoading) return
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请填写完整信息")
            return
        }
        _uiState.value = LoginUiState(isLoading = true)
        viewModelScope.launch {
            sessionManager.login(serverUrl, username, password).fold(
                onSuccess = { onSuccess() },
                onFailure = { e ->
                    _uiState.value = LoginUiState(error = e.message ?: "登录失败")
                }
            )
        }
    }
}
