package com.examcamp.app

import android.app.Application
import com.examcamp.app.auth.AuthManager
import com.examcamp.app.data.LocalStore
import com.examcamp.app.util.NetworkMonitor

class ExamcampApp : Application() {
    lateinit var store: LocalStore
        private set
    lateinit var auth: AuthManager
        private set
    lateinit var network: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        store = LocalStore(this)
        auth = AuthManager(this)
        network = NetworkMonitor(this)
    }
}
