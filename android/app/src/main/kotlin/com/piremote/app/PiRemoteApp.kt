package com.piremote.app

import android.app.Application
import com.piremote.app.data.SessionRepository

class PiRemoteApp : Application() {

    lateinit var repository: SessionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SessionRepository(this)
    }
}
