package com.mcpai.chat

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mcpai.chat.BuildConfig
import com.mcpai.chat.data.ChatRepository
import com.mcpai.chat.service.AnswerService
import com.mcpai.chat.service.AppForeground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Process owner for the shared [ChatRepository].
 *
 * The repository is bound to the application context and lives for the whole
 * process. Activities must never call `close()` on it: closing an activity is
 * not closing the app, and the database and MCP sessions are safe to keep
 * alive while the user is on another screen.
 *
 * This is also where the answer service gets started: when a turn begins the
 * application promotes [AnswerService] to the foreground so the answer keeps
 * going when the user leaves the app.
 */
class ChatApplication : Application() {

    lateinit var repository: ChatRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Release builds plant no trees, so diagnostics never reach logcat/release builds.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        repository = ChatRepository(this)
        AppForeground.register(this)
        AnswerService.createChannels(this)

        // Start the answer service when a turn starts. Turns begin from the
        // UI (foreground), so promoting the service is allowed; wrap it anyway
        // so a background start attempt degrades to "turn runs without
        // notifications" instead of crashing the app.
        appScope.launch {
            repository.sending.collect { sending ->
                if (sending) {
                    runCatching {
                        ContextCompat.startForegroundService(
                            this@ChatApplication,
                            Intent(this@ChatApplication, AnswerService::class.java),
                        )
                    }.onFailure { error ->
                        Timber.w(error, "could not start AnswerService")
                    }
                }
            }
        }
    }

    override fun onTerminate() {
        repository.close()
        super.onTerminate()
    }
}
