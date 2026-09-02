package com.chobgroup.admin_vlesshub

import android.app.Application
import com.chobgroup.admin_vlesshub.data.AdminKeyStore
import com.chobgroup.admin_vlesshub.data.HiddenStore

class AdminApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdminKeyStore.instance.init(this)
        HiddenStore.instance.init(this)
    }
}
