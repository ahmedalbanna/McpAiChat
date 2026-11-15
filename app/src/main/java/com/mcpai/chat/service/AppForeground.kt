package com.mcpai.chat.service

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks whether any activity of this process is started (i.e. visible).
 *
 * The answer service uses this to decide whether the user can already see the
 * turn's outcome in the UI, or whether a notification is needed instead.
 */
object AppForeground : Application.ActivityLifecycleCallbacks {

    private val started = AtomicInteger(0)
    private val _inForeground = MutableStateFlow(false)
    val inForeground: StateFlow<Boolean> = _inForeground.asStateFlow()

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    /** Synchronous view for non-collecting code paths. */
    fun isInForeground(): Boolean = started.get() > 0

    override fun onActivityStarted(activity: Activity) {
        if (started.incrementAndGet() > 0) _inForeground.value = true
    }

    override fun onActivityStopped(activity: Activity) {
        if (started.decrementAndGet() <= 0) _inForeground.value = false
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
