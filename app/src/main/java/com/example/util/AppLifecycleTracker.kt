package com.example.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks application, activity, and overlay service lifecycle state
 * to verify runtime context and permission consistency during clipboard access.
 */
object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {

    private val resumedActivityCount = AtomicInteger(0)
    private val startedActivityCount = AtomicInteger(0)
    private val createdActivityCount = AtomicInteger(0)
    private val isOverlayServiceRunningFlag = AtomicBoolean(false)

    @Volatile
    private var lastActiveActivityName: String? = null

    fun isAppInForeground(): Boolean = resumedActivityCount.get() > 0

    fun isOverlayServiceRunning(): Boolean = isOverlayServiceRunningFlag.get()

    fun setOverlayServiceRunning(running: Boolean) {
        isOverlayServiceRunningFlag.set(running)
    }

    /**
     * Determines the current lifecycle state classification.
     */
    fun determineLifecycleState(): ClipboardLifecycleState {
        return when {
            resumedActivityCount.get() > 0 -> ClipboardLifecycleState.FOREGROUND_ACTIVITY
            startedActivityCount.get() > 0 -> ClipboardLifecycleState.STARTED_ACTIVITY
            createdActivityCount.get() > 0 -> ClipboardLifecycleState.CREATED_ACTIVITY
            isOverlayServiceRunningFlag.get() -> ClipboardLifecycleState.FOREGROUND_SERVICE
            else -> ClipboardLifecycleState.BACKGROUND_PROCESS
        }
    }

    /**
     * Captures a complete snapshot of permissions, focus, and lifecycle state.
     */
    fun captureEnvironment(context: Context, callerContext: String): ClipboardEnvironmentSnapshot {
        val hasOverlayPermission = try {
            Settings.canDrawOverlays(context)
        } catch (_: Exception) {
            false
        }

        val lifecycleState = determineLifecycleState()
        val inForeground = isAppInForeground()
        val overlayActive = isOverlayServiceRunning()
        val activityName = lastActiveActivityName ?: "None"

        val detail = "State=$lifecycleState, InForeground=$inForeground, OverlayActive=$overlayActive, TopActivity=$activityName"

        return ClipboardEnvironmentSnapshot(
            callerContext = callerContext,
            hasOverlayPermission = hasOverlayPermission,
            lifecycleState = lifecycleState,
            lifecycleStateDetail = detail,
            isAppInForeground = inForeground,
            isOverlayServiceRunning = overlayActive
        )
    }

    // --- ActivityLifecycleCallbacks ---

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        createdActivityCount.incrementAndGet()
        lastActiveActivityName = activity.javaClass.simpleName
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount.incrementAndGet()
        lastActiveActivityName = activity.javaClass.simpleName
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivityCount.incrementAndGet()
        lastActiveActivityName = activity.javaClass.simpleName
    }

    override fun onActivityPaused(activity: Activity) {
        resumedActivityCount.decrementAndGet().coerceAtLeast(0)
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount.decrementAndGet().coerceAtLeast(0)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        createdActivityCount.decrementAndGet().coerceAtLeast(0)
        if (createdActivityCount.get() == 0) {
            lastActiveActivityName = null
        }
    }
}
