package com.projectz.cannyminute.data.settings

data class AppSettings(
    val protectionEnabled: Boolean = false,
    val cooldownDurationSeconds: Int = 10,
    val allowListPackages: Set<String> = emptySet(),
    val temporaryBypassUntilEpochMillis: Long = 0L,
    val diagnosticsEnabled: Boolean = false
) {
    fun isBypassActive(nowEpochMillis: Long): Boolean {
        return nowEpochMillis < temporaryBypassUntilEpochMillis
    }

    companion object {
        val DEFAULT = AppSettings()
    }
}

