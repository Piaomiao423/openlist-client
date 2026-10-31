package com.openlist.client.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.openlist.client.data.model.LoginRequest
import com.openlist.client.data.remote.ApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "openlist_session")

/** 一个已保存的账户 */
data class Account(
    val serverUrl: String,
    val username: String,
    val token: String
) {
    /** 账户唯一标识：serverUrl + username */
    val id: String get() = "$serverUrl|$username"
}

/**
 * 多账户会话管理：
 * - 保存多个账户（server / username / token）
 * - 一个当前活跃账户，决定 authState 与 getServerUrl/getToken
 * - JSON 编码持久化在 DataStore
 */
class SessionManager(private val context: Context) {

    companion object {
        private val KEY_ACCOUNTS = stringPreferencesKey("accounts")
        private val KEY_ACTIVE = stringPreferencesKey("active")
    }

    /** 当前活跃账户（含服务器、用户名、Token），未登录时为 null */
    val activeAccountFlow: Flow<Account?> = context.dataStore.data
        .map { prefs ->
            val activeId = prefs[KEY_ACTIVE] ?: return@map null
            decode(prefs[KEY_ACCOUNTS]).firstOrNull { it.id == activeId }
        }

    /** true = 已有活跃账户（已登录） */
    val authState: Flow<Boolean?> = activeAccountFlow.map { it != null }

    /** 已保存账户列表 */
    val accountsFlow: Flow<List<Account>> = context.dataStore.data
        .map { prefs -> decode(prefs[KEY_ACCOUNTS]) }

    suspend fun getActiveAccount(): Account? {
        val prefs = context.dataStore.data.first()
        val activeId = prefs[KEY_ACTIVE] ?: return null
        return decode(prefs[KEY_ACCOUNTS]).firstOrNull { it.id == activeId }
    }

    suspend fun getServerUrl(): String = getActiveAccount()?.serverUrl ?: ""

    suspend fun getToken(): String = getActiveAccount()?.token ?: ""

    suspend fun login(serverUrl: String, username: String, password: String): Result<Unit> {
        return try {
            val normalizedUrl = serverUrl.trim().trimEnd('/')
            val api = ApiClient.getApi(normalizedUrl)
            val response = api.login(LoginRequest(username.trim(), password))
            if (response.isSuccessful) {
                val body = response.body()
                val token = body?.data?.token
                if (body?.code == 200 && !token.isNullOrBlank()) {
                    val account = Account(normalizedUrl, username.trim(), token)
                    context.dataStore.edit { prefs ->
                        val list = decode(prefs[KEY_ACCOUNTS])
                            .filterNot { it.id == account.id } + account
                        prefs[KEY_ACCOUNTS] = encode(list)
                        prefs[KEY_ACTIVE] = account.id
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "登录失败"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 切换到已保存账户（不重新认证，直接使用其 Token） */
    suspend fun switchAccount(serverUrl: String, username: String): Boolean {
        val account = context.dataStore.data.first().let { prefs ->
            decode(prefs[KEY_ACCOUNTS]).firstOrNull {
                it.serverUrl == serverUrl && it.username == username
            }
        } ?: return false
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE] = account.id
        }
        ApiClient.reset()
        return true
    }

    /** 删除已保存账户；若删除的是当前账户则退出登录，回到服务器列表重新选择 */
    suspend fun removeAccount(serverUrl: String, username: String) {
        val targetId = "$serverUrl|$username"
        context.dataStore.edit { prefs ->
            val list = decode(prefs[KEY_ACCOUNTS]).filterNot { it.id == targetId }
            prefs[KEY_ACCOUNTS] = encode(list)
            if (prefs[KEY_ACTIVE] == targetId) {
                prefs.remove(KEY_ACTIVE)
            }
        }
        ApiClient.reset()
    }

    /** 退出当前账户：清除其 Token 并登出，保留其他账户供服务器列表切换 */
    suspend fun logout() {
        val active = getActiveAccount() ?: return
        removeAccount(active.serverUrl, active.username)
    }

    // ===== JSON 编解码 =====

    private fun encode(accounts: List<Account>): String = JSONArray().apply {
        accounts.forEach { acc ->
            put(JSONObject().apply {
                put("server", acc.serverUrl)
                put("user", acc.username)
                put("token", acc.token)
            })
        }
    }.toString()

    private fun decode(raw: String?): List<Account> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val server = obj.optString("server", "")
                val user = obj.optString("user", "")
                val token = obj.optString("token", "")
                if (server.isNotBlank() && token.isNotBlank()) {
                    Account(server, user, token)
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
