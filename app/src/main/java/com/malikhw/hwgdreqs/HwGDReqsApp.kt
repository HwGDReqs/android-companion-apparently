package com.malikhw.hwgdreqs

import android.app.Application
import com.malikhw.hwgdreqs.data.AuthPreferences
import com.malikhw.hwgdreqs.network.HwGDReqsApi

class HwGDReqsApp : Application() {
    val authPrefs: AuthPreferences by lazy { AuthPreferences(this) }
    val api: HwGDReqsApi by lazy { HwGDReqsApi(authPrefs) }
}
