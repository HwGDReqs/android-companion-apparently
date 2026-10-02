package com.malikhw.hwgdreqs.data

import android.content.Context
import android.os.Build
import java.util.UUID

class AuthPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("hwgdreqs_auth", Context.MODE_PRIVATE)

    val deviceId: String
        @Synchronized get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).commit()
            return id
        }

    val deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null)
            ?: Build.MODEL?.takeIf { it.isNotBlank() }
            ?: "Android Device"

    fun getToken(host: String): String? = prefs.getString(tokenKey(host), null)

    fun setToken(host: String, token: String) {
        prefs.edit().putString(tokenKey(host), token).apply()
    }

    fun clearToken(host: String) {
        prefs.edit().remove(tokenKey(host)).apply()
    }

    private fun tokenKey(host: String) = "token_$host"

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_NAME = "device_name"
    }
}
