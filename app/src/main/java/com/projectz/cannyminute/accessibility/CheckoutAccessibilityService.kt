package com.projectz.cannyminute.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.projectz.cannyminute.data.log.DetectionDecisionLog
import com.projectz.cannyminute.data.log.DetectionEventLogger
import com.projectz.cannyminute.data.settings.AppSettings
import com.projectz.cannyminute.data.settings.AppSettingsRepository
import com.projectz.cannyminute.detection.CheckoutRulesEngine
import com.projectz.cannyminute.detection.UiSnapshot
import com.projectz.cannyminute.R
import com.projectz.cannyminute.ui.cooldown.CooldownActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CheckoutAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var settingsRepository: AppSettingsRepository

    @Inject
    lateinit var checkoutRulesEngine: CheckoutRulesEngine

    @Inject
    lateinit var detectionEventLogger: DetectionEventLogger

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsState = MutableStateFlow(AppSettings.DEFAULT)

    private val pendingMatches = mutableMapOf<String, PendingMatch>()
    private val lastTriggerByPackage = mutableMapOf<String, Long>()
    private val lastEvaluationByPackage = mutableMapOf<String, Long>()
    private val lastLoggedAtByActionPackage = mutableMapOf<String, Long>()
    private val suppressedUntilByApp = mutableMapOf<String, Long>()
    private val suppressedUntilByOrigin = mutableMapOf<String, Long>()
    private var lastGlobalTriggerAt: Long = 0L
    private var cachedAccessibilityEnabled: Boolean = false
    private var lastAccessibilityStateCheckAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        ensureNotificationChannel()
        serviceScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                settingsState.value = settings
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isSupportedEventType(event.eventType)) return

        val sourcePackageName = event.packageName?.toString()?.trim().orEmpty()
        if (sourcePackageName.isEmpty() || sourcePackageName == packageName) return
        if (IGNORED_SOURCE_PACKAGES.contains(sourcePackageName)) return
        if (CooldownActivity.isVisible) return

        val settings = settingsState.value
        if (!settings.protectionEnabled) return

        val now = System.currentTimeMillis()
        if (!isAccessibilityServiceFullyEnabled(now)) {
            logDecision(
                packageName = sourcePackageName,
                confidence = 0f,
                action = ACTION_IGNORED_ACCESSIBILITY_DISABLED,
                matchedSignals = emptyList()
            )
            return
        }

        if (isSuppressedApp(sourcePackageName, now)) {
            logDecision(
                packageName = sourcePackageName,
                confidence = 0f,
                action = ACTION_IGNORED_APP_SUPPRESSION,
                matchedSignals = originSignals(sourcePackageName, null)
            )
            return
        }

        val lastEvaluationAt = lastEvaluationByPackage[sourcePackageName] ?: 0L
        if (now - lastEvaluationAt < MIN_EVALUATION_INTERVAL_MS) return
        lastEvaluationByPackage[sourcePackageName] = now

        if (settings.allowListPackages.contains(sourcePackageName)) {
            logDecision(sourcePackageName, 0f, ACTION_IGNORED_ALLOWLIST, emptyList())
            return
        }

        if (ContinueSuppressionRegistry.isSuppressed(sourcePackageName, now)) {
            logDecision(sourcePackageName, 0f, ACTION_IGNORED_CONTINUE_SUPPRESSION, emptyList())
            return
        }

        if (settings.isBypassActive(now)) {
            logDecision(sourcePackageName, 0f, ACTION_IGNORED_BYPASS, emptyList())
            return
        }

        val snapshot = buildUiSnapshot(sourcePackageName)
        if (snapshot.visibleTexts.isEmpty() && snapshot.viewIds.isEmpty()) return
        val originHost = inferOriginHost(snapshot.visibleTexts)
        val originSuppressionKey = buildOriginSuppressionKey(sourcePackageName, originHost)
        if (isSuppressedOrigin(originSuppressionKey, now)) {
            logDecision(
                packageName = sourcePackageName,
                confidence = 0f,
                action = ACTION_IGNORED_ORIGIN_SUPPRESSION,
                matchedSignals = originSignals(sourcePackageName, originHost)
            )
            return
        }

        val result = checkoutRulesEngine.evaluate(snapshot)
        if (!result.shouldTrigger) {
            if (result.confidence >= LOGGING_FLOOR_CONFIDENCE) {
                logDecision(sourcePackageName, result.confidence, ACTION_BELOW_THRESHOLD, result.matchedSignals)
            }
            pendingMatches.remove(sourcePackageName)
            return
        }

        val lastTriggerAt = lastTriggerByPackage[sourcePackageName] ?: 0L
        if (now - lastTriggerAt < TRIGGER_DEBOUNCE_MS) {
            logDecision(sourcePackageName, result.confidence, ACTION_IGNORED_DEBOUNCE, result.matchedSignals)
            return
        }

        if (now - lastGlobalTriggerAt < GLOBAL_TRIGGER_DEBOUNCE_MS) {
            logDecision(sourcePackageName, result.confidence, ACTION_IGNORED_GLOBAL_DEBOUNCE, result.matchedSignals)
            return
        }

        if (!confirmTrigger(sourcePackageName, result.confidence, result.matchedSignals, now)) {
            return
        }

        lastGlobalTriggerAt = now
        lastTriggerByPackage[sourcePackageName] = now
        pendingMatches.remove(sourcePackageName)
        registerTriggerSuppression(sourcePackageName, originSuppressionKey, now)

        launchCooldown(sourcePackageName, settings.cooldownDurationSeconds, result.confidence)
        logDecision(
            packageName = sourcePackageName,
            confidence = result.confidence,
            action = ACTION_TRIGGERED,
            matchedSignals = result.matchedSignals + originSignals(sourcePackageName, originHost)
        )
    }

    override fun onInterrupt() {
        // No-op for MVP.
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun confirmTrigger(
        packageName: String,
        confidence: Float,
        matchedSignals: List<String>,
        now: Long
    ): Boolean {
        val pendingMatch = pendingMatches[packageName]
        if (pendingMatch == null || now - pendingMatch.firstSeenAt > CONFIRM_WINDOW_MS) {
            pendingMatches[packageName] = PendingMatch(
                firstSeenAt = now,
                hitCount = 1,
                confidence = confidence,
                matchedSignals = matchedSignals
            )
            return false
        }

        val updatedHits = pendingMatch.hitCount + 1
        pendingMatches[packageName] = pendingMatch.copy(
            hitCount = updatedHits,
            confidence = maxOf(pendingMatch.confidence, confidence),
            matchedSignals = (pendingMatch.matchedSignals + matchedSignals).distinct()
        )

        return updatedHits >= REQUIRED_HITS
    }

    private fun launchCooldown(packageName: String, durationSeconds: Int, confidence: Float) {
        if (!AccessibilityServiceState.isCheckoutServiceEnabled(this)) {
            Log.i(TAG, "Skipping cooldown launch because accessibility service is disabled")
            return
        }

        val intent = Intent(this, CooldownActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(CooldownActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(CooldownActivity.EXTRA_DURATION_SECONDS, durationSeconds)
            putExtra(CooldownActivity.EXTRA_CONFIDENCE, confidence)
        }

        try {
            startActivity(intent)
        } catch (throwable: Throwable) {
            Log.w(TAG, "Unable to launch cooldown activity", throwable)
            postFallbackNotification(intent, packageName)
        }
    }

    private fun postFallbackNotification(cooldownIntent: Intent, sourcePackageName: String) {
        val requestCode = sourcePackageName.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            cooldownIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.cooldown_notification_title))
            .setContentText(getString(R.string.cooldown_notification_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        try {
            notificationManager.notify(NOTIFICATION_ID_BASE + requestCode, notification)
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Unable to post fallback notification", securityException)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        val existingChannel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
        if (existingChannel != null) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.cooldown_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.cooldown_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildUiSnapshot(sourcePackageName: String): UiSnapshot {
        val rootNode = rootInActiveWindow ?: return UiSnapshot(sourcePackageName, emptySet(), emptySet())
        val visibleTexts = linkedSetOf<String>()
        val viewIds = linkedSetOf<String>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(rootNode)

        var visitedNodes = 0
        while (stack.isNotEmpty() && visitedNodes < MAX_NODE_VISITS) {
            val node = stack.removeLast()
            try {
                visitedNodes += 1

                collectText(node.text?.toString(), visibleTexts)
                collectText(node.contentDescription?.toString(), visibleTexts)

                node.viewIdResourceName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { viewIds += it }

                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(stack::addLast)
                }
            } finally {
                node.recycle()
            }
        }

        return UiSnapshot(
            packageName = sourcePackageName,
            visibleTexts = visibleTexts,
            viewIds = viewIds
        )
    }

    private fun collectText(rawValue: String?, destination: MutableSet<String>) {
        val cleaned = rawValue
            ?.trim()
            ?.replace("\\s+".toRegex(), " ")
            ?.take(MAX_TEXT_LENGTH)
            ?: return

        if (cleaned.length >= MIN_TEXT_LENGTH) {
            destination += cleaned
        }
    }

    private fun logDecision(
        packageName: String,
        confidence: Float,
        action: String,
        matchedSignals: List<String>
    ) {
        val now = System.currentTimeMillis()
        if (!shouldLogDecision(action, packageName, now)) return

        val diagnosticsEnabled = settingsState.value.diagnosticsEnabled
        serviceScope.launch {
            detectionEventLogger.log(
                DetectionDecisionLog(
                    timestampEpochMillis = now,
                    packageName = packageName,
                    confidence = confidence,
                    action = action,
                    matchedSignals = matchedSignals
                ),
                diagnosticsEnabled = diagnosticsEnabled
            )
        }
    }

    private fun shouldLogDecision(action: String, packageName: String, now: Long): Boolean {
        if (action == ACTION_TRIGGERED) return true

        val key = "$action|$packageName"
        val lastLoggedAt = lastLoggedAtByActionPackage[key] ?: 0L
        if (now - lastLoggedAt < LOG_DEBOUNCE_MS) {
            return false
        }
        lastLoggedAtByActionPackage[key] = now
        return true
    }

    private fun isSuppressedApp(packageName: String, now: Long): Boolean {
        val suppressUntil = suppressedUntilByApp[packageName] ?: return false
        if (now >= suppressUntil) {
            suppressedUntilByApp.remove(packageName, suppressUntil)
            return false
        }
        return true
    }

    private fun isSuppressedOrigin(originKey: String?, now: Long): Boolean {
        if (originKey.isNullOrEmpty()) return false
        val suppressUntil = suppressedUntilByOrigin[originKey] ?: return false
        if (now >= suppressUntil) {
            suppressedUntilByOrigin.remove(originKey, suppressUntil)
            return false
        }
        return true
    }

    private fun registerTriggerSuppression(packageName: String, originKey: String?, now: Long) {
        val suppressUntil = now + RETRIGGER_SUPPRESSION_MS
        if (isBrowserPackage(packageName) && !originKey.isNullOrEmpty()) {
            suppressedUntilByOrigin[originKey] = suppressUntil
        } else {
            suppressedUntilByApp[packageName] = suppressUntil
        }
    }

    private fun inferOriginHost(visibleTexts: Set<String>): String? {
        for (text in visibleTexts) {
            val normalized = text.lowercase(Locale.US).trim()
            val match = HOST_PATTERN.find(normalized) ?: continue
            val host = match.groupValues[1]
                .removePrefix("www.")
                .trim('.')
                .take(MAX_HOST_LENGTH)

            if (host.count { it == '.' } >= 1) {
                return host
            }
        }
        return null
    }

    private fun buildOriginSuppressionKey(packageName: String, originHost: String?): String? {
        if (!isBrowserPackage(packageName)) return null
        if (originHost.isNullOrBlank()) return null
        return "$packageName|$originHost"
    }

    private fun originSignals(packageName: String, originHost: String?): List<String> {
        val signals = mutableListOf("origin_package:$packageName")
        if (!originHost.isNullOrBlank()) {
            signals += "origin_host:$originHost"
        }
        return signals
    }

    private fun isBrowserPackage(packageName: String): Boolean {
        return BROWSER_PACKAGES.contains(packageName)
    }

    private fun isSupportedEventType(type: Int): Boolean {
        return type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_CLICKED
    }

    private fun isAccessibilityServiceFullyEnabled(now: Long): Boolean {
        if (now - lastAccessibilityStateCheckAt < ACCESSIBILITY_STATE_CACHE_MS) {
            return cachedAccessibilityEnabled
        }

        lastAccessibilityStateCheckAt = now
        cachedAccessibilityEnabled = AccessibilityServiceState.isCheckoutServiceEnabled(this)
        return cachedAccessibilityEnabled
    }

    private data class PendingMatch(
        val firstSeenAt: Long,
        val hitCount: Int,
        val confidence: Float,
        val matchedSignals: List<String>
    )

    private companion object {
        const val TAG = "CheckoutAccessService"
        const val MAX_NODE_VISITS = 160
        const val MAX_TEXT_LENGTH = 120
        const val MIN_TEXT_LENGTH = 2
        const val CONFIRM_WINDOW_MS = 4_000L
        const val TRIGGER_DEBOUNCE_MS = 30_000L
        const val GLOBAL_TRIGGER_DEBOUNCE_MS = 15_000L
        const val RETRIGGER_SUPPRESSION_MS = 3 * 60_000L
        const val MIN_EVALUATION_INTERVAL_MS = 500L
        const val LOG_DEBOUNCE_MS = 8_000L
        const val REQUIRED_HITS = 2
        const val LOGGING_FLOOR_CONFIDENCE = 0.40f
        const val MAX_HOST_LENGTH = 96
        const val ACCESSIBILITY_STATE_CACHE_MS = 2_000L
        const val NOTIFICATION_ID_BASE = 40_000
        const val NOTIFICATION_CHANNEL_ID = "cooldown_interruptions"

        const val ACTION_TRIGGERED = "triggered"
        const val ACTION_BELOW_THRESHOLD = "below_threshold"
        const val ACTION_IGNORED_ALLOWLIST = "ignored_allowlist"
        const val ACTION_IGNORED_CONTINUE_SUPPRESSION = "ignored_continue_suppression"
        const val ACTION_IGNORED_APP_SUPPRESSION = "ignored_app_suppression"
        const val ACTION_IGNORED_ORIGIN_SUPPRESSION = "ignored_origin_suppression"
        const val ACTION_IGNORED_BYPASS = "ignored_bypass"
        const val ACTION_IGNORED_DEBOUNCE = "ignored_debounce"
        const val ACTION_IGNORED_GLOBAL_DEBOUNCE = "ignored_global_debounce"
        const val ACTION_IGNORED_ACCESSIBILITY_DISABLED = "ignored_accessibility_disabled"

        val IGNORED_SOURCE_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )

        val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.sec.android.app.sbrowser"
        )

        val HOST_PATTERN = Regex(
            pattern = "(?:https?://)?(?:www\\.)?([a-z0-9][a-z0-9.-]+\\.[a-z]{2,})(?:[:/\\s]|$)",
            option = RegexOption.IGNORE_CASE
        )
    }
}

