package com.mcpai.chat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mcpai.chat.R
import com.mcpai.chat.data.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Keeps a background answer alive while the app is away.
 *
 * The turn runs in the repository (see [ChatRepository.startTurn]); this service
 * owns only the process-visible contract of that turn:
 *
 * - an ongoing, low-importance notification while an answer is being written
 *   ("Writing your answer…"), with a Cancel action;
 * - a higher-importance "Approval needed" notification when the engine waits on
 *   a tool approval while the app is not in the foreground;
 * - an "Answer ready" notification when the turn ends and nobody is looking at
 *   the app.
 *
 * The service is started when [ChatRepository.sending] flips to true and stops
 * itself when the turn ends. It is not a bound service.
 */
class AnswerService : Service() {

    companion object {
        const val CHANNEL_WORK = "answers_work"
        const val CHANNEL_DONE = "answers_done"

        const val NOTIF_ONGOING = 4101
        const val NOTIF_DONE = 4102
        const val NOTIF_APPROVAL = 4103

        const val ACTION_CANCEL = "com.mcpai.chat.action.CANCEL_TURN"
        const val ACTION_OPEN_CHAT = "com.mcpai.chat.action.OPEN_CHAT"

        /** Idempotent; safe to call from both the Application and the service. */
        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WORK,
                    context.getString(R.string.channel_answers_work_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_answers_work_desc)
                    setShowBadge(false)
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DONE,
                    context.getString(R.string.channel_answers_done_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_answers_done_desc)
                },
            )
        }
    }

    private val repo by lazy { ChatRepository.get(applicationContext) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True once this service observed a turn actually running. */
    private var seenRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        Timber.tag("AnswerService").i("onCreate")
        scope.launch {
            combine(repo.sending, repo.pendingApproval, AppForeground.inForeground) { sending, approval, fg ->
                Triple(sending, approval, fg)
            }.collect { (sending, approval, fg) ->
                runCatching {
                    if (sending) {
                        seenRunning = true
                        manager().notify(NOTIF_ONGOING, ongoingNotification(approval))
                        if (approval != null && !fg) {
                            manager().notify(NOTIF_APPROVAL, approvalNotification(approval))
                        } else {
                            manager().cancel(NOTIF_APPROVAL)
                        }
                    } else if (seenRunning) {
                        seenRunning = false
                        manager().cancel(NOTIF_ONGOING)
                        manager().cancel(NOTIF_APPROVAL)
                        Timber.tag("AnswerService").i("turn ended; fg=%s cancelled=%s", fg, repo.lastTurnCancelledFlag)
                        if (!fg && !repo.lastTurnCancelledFlag) {
                            manager().notify(NOTIF_DONE, doneNotification())
                        }
                        stopSelf()
                    }
                }.onFailure { error ->
                    Timber.tag("AnswerService").e(error, "collector step failed")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag("AnswerService").i("onStartCommand action=%s", intent?.action)
        startForeground(
            NOTIF_ONGOING,
            ongoingNotification(repo.pendingApproval.value),
        )
        if (intent?.action == ACTION_CANCEL) {
            repo.cancelTurn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Timber.tag("AnswerService").i("onDestroy")
        scope.cancel()
        super.onDestroy()
    }

    private fun ongoingNotification(
        approval: com.mcpai.chat.data.ChatEvent.ToolApprovalRequested?,
    ): Notification {
        val waiting = approval != null
        val text = if (waiting) {
            getString(R.string.notification_waiting_for_approval, approval!!.name)
        } else {
            getString(R.string.notification_writing_answer)
        }
        return NotificationCompat.Builder(this, CHANNEL_WORK)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openChatPendingIntent())
            .addAction(
                R.mipmap.ic_launcher,
                getString(R.string.action_cancel),
                cancelPendingIntent(),
            )
            .build()
    }

    private fun approvalNotification(
        approval: com.mcpai.chat.data.ChatEvent.ToolApprovalRequested,
    ): Notification =
        NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_approval_needed_title))
            .setContentText(
                getString(
                    R.string.notification_approval_needed_text,
                    approval.serverName,
                    approval.name,
                ),
            )
            .setAutoCancel(true)
            .setContentIntent(openChatPendingIntent())
            .build()

    private fun doneNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_answer_ready_title))
            .setContentText(getString(R.string.notification_answer_ready_text))
            .setAutoCancel(true)
            .setContentIntent(openChatPendingIntent())
            .build()

    /** Deep-links into the conversation that was being written, if any. */
    private fun openChatPendingIntent(): PendingIntent {
        val conversationId = repo.currentConversationId()
        val intent = Intent(this, com.mcpai.chat.ui.MainActivity::class.java).apply {
            putExtra("conversation_id", conversationId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentMutabilityFlag(),
        )
    }

    private fun cancelPendingIntent(): PendingIntent {
        val intent = Intent(this, AnswerService::class.java).setAction(ACTION_CANCEL)
        return PendingIntent.getService(
            this,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentMutabilityFlag(),
        )
    }

    private fun pendingIntentMutabilityFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun manager(): NotificationManager =
        getSystemService(NotificationManager::class.java)
}
