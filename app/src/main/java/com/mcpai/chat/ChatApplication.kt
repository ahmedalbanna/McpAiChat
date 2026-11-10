package com.mcpai.chat

import android.app.Application
import com.mcpai.chat.BuildConfig
import com.mcpai.chat.data.ChatRepository
import timber.log.Timber

/**
 * Process owner for the shared [ChatRepository].
 *
 * The repository is bound to the application context and lives for the whole
 * process. Activities must never call `close()` on it: closing an activity is
 * not closing the app, and the database and MCP sessions are safe to keep
 * alive while the user is on another screen.
 */
class ChatApplication : Application() {

    lateinit var repository: ChatRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Release builds plant no trees, so diagnostics never reach logcat/release builds.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        repository = ChatRepository(this)
    }

    override fun onTerminate() {
        repository.close()
        super.onTerminate()
    }
}
