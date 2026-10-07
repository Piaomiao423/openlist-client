package com.openlist.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openlist.client.util.Account
import com.openlist.client.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

    /** 已保存账户列表（多账户快速切换） */
    val accounts: StateFlow<List<Account>> =
        sessionManager.accountsFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun login(serverUrl: String, username: String, password: String, onSuccess: () -> Unit) {
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

    fun switchAccount(account: Account, onSuccess: () -> Unit) {
        _uiState.value = LoginUiState(isLoading = true)
        viewModelScope.launch {
            val ok = sessionManager.switchAccount(account.serverUrl, account.username)
            _uiState.value = LoginUiState()
            if (ok) onSuccess()
            else _uiState.value = LoginUiState(error = "账户已失效，请重新登录")
        }
    }

    fun removeAccount(account: Account) {
        viewModelScope.launch {
            sessionManager.removeAccount(account.serverUrl, account.username)
        }
    }
}
