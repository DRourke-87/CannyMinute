package com.projectz.cannyminute.accessibility

import java.util.concurrent.ConcurrentHashMap

object ContinueSuppressionRegistry {
    private val suppressUntilByPackage = ConcurrentHashMap<String, Long>()

    fun suppress(packageName: String, durationMillis: Long) {
        val safePackageName = packageName.trim()
        if (safePackageName.isEmpty()) return

        val now = System.currentTimeMillis()
        suppressUntilByPackage[safePackageName] = now + durationMillis.coerceAtLeast(1_000L)
    }

    fun isSuppressed(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val safePackageName = packageName.trim()
        if (safePackageName.isEmpty()) return false

        val suppressUntil = suppressUntilByPackage[safePackageName] ?: return false
        if (now >= suppressUntil) {
            suppressUntilByPackage.remove(safePackageName, suppressUntil)
            return false
        }
        return true
    }
}

