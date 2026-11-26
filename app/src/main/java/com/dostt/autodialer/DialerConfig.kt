package com.dostt.autodialer

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists user preferences for ON/OFF state, cooldown timing,
 * max retry limits, call-connect shutoff, and screen-percentage tap fallbacks.
 */
class DialerConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("dostt_dialer_prefs", Context.MODE_PRIVATE)

    /** Master ON/OFF switch controlled by the user in MainActivity. */
    var isMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MASTER_ENABLED, value).apply()

    /** Active dialing session state (always cleared on call connect, switch OFF, or leaving Dostt). */
    var isArmed: Boolean
        get() = prefs.getBoolean(KEY_IS_ARMED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ARMED, value).apply()

    /** When true, calls disableSelf() as soon as a call connects so zero service stays bound. */
    var disableServiceOnConnect: Boolean
        get() = prefs.getBoolean(KEY_DISABLE_ON_CONNECT, false)
        set(value) = prefs.edit().putBoolean(KEY_DISABLE_ON_CONNECT, value).apply()

    var retryDelayMs: Long
        get() = prefs.getLong(KEY_RETRY_DELAY_MS, 1500L).coerceIn(500L, 15000L)
        set(value) = prefs.edit().putLong(KEY_RETRY_DELAY_MS, value.coerceIn(500L, 15000L)).apply()

    var maxAttempts: Int
        get() = prefs.getInt(KEY_MAX_ATTEMPTS, 15).coerceIn(1, 100)
        set(value) = prefs.edit().putInt(KEY_MAX_ATTEMPTS, value.coerceIn(1, 100)).apply()

    var useCoordinateFallback: Boolean
        get() = prefs.getBoolean(KEY_USE_COORD_FALLBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_COORD_FALLBACK, value).apply()

    var collapsedAudioXPercent: Float
        get() = prefs.getFloat(KEY_COLLAPSED_X, 77.5f)
        set(value) = prefs.edit().putFloat(KEY_COLLAPSED_X, value.coerceIn(1f, 99f)).apply()

    var collapsedAudioYPercent: Float
        get() = prefs.getFloat(KEY_COLLAPSED_Y, 84.3f)
        set(value) = prefs.edit().putFloat(KEY_COLLAPSED_Y, value.coerceIn(1f, 99f)).apply()

    var expandedAudioXPercent: Float
        get() = prefs.getFloat(KEY_EXPANDED_X, 89.5f)
        set(value) = prefs.edit().putFloat(KEY_EXPANDED_X, value.coerceIn(1f, 99f)).apply()

    var expandedAudioYPercent: Float
        get() = prefs.getFloat(KEY_EXPANDED_Y, 69.5f)
        set(value) = prefs.edit().putFloat(KEY_EXPANDED_Y, value.coerceIn(1f, 99f)).apply()

    var goHomeXPercent: Float
        get() = prefs.getFloat(KEY_HOME_BTN_X, 50.0f)
        set(value) = prefs.edit().putFloat(KEY_HOME_BTN_X, value.coerceIn(1f, 99f)).apply()

    var goHomeYPercent: Float
        get() = prefs.getFloat(KEY_HOME_BTN_Y, 58.0f)
        set(value) = prefs.edit().putFloat(KEY_HOME_BTN_Y, value.coerceIn(1f, 99f)).apply()

    companion object {
        const val DOSTT_PACKAGE = "live.friend.dostt"

        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_IS_ARMED = "is_armed"
        private const val KEY_DISABLE_ON_CONNECT = "disable_on_connect"
        private const val KEY_RETRY_DELAY_MS = "retry_delay_ms"
        private const val KEY_MAX_ATTEMPTS = "max_attempts"
        private const val KEY_USE_COORD_FALLBACK = "use_coord_fallback"
        private const val KEY_COLLAPSED_X = "collapsed_x"
        private const val KEY_COLLAPSED_Y = "collapsed_y"
        private const val KEY_EXPANDED_X = "expanded_x"
        private const val KEY_EXPANDED_Y = "expanded_y"
        private const val KEY_HOME_BTN_X = "home_btn_x"
        private const val KEY_HOME_BTN_Y = "home_btn_y"
    }
}
