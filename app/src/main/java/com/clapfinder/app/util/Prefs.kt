package com.clapfinder.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Thin typed wrapper over SharedPreferences for all persisted app state. */
class Prefs private constructor(ctx: Context) {

    private val sp = ctx.applicationContext.getSharedPreferences("clapfinder", Context.MODE_PRIVATE)

    /** Lets Compose screens refresh when a value changes (e.g. the service stops from its notification). */
    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)

    var onboardingDone: Boolean
        get() = sp.getBoolean(K_ONBOARDING, false)
        set(v) = sp.edit { putBoolean(K_ONBOARDING, v) }

    var flashMode: Boolean
        get() = sp.getBoolean(K_FLASH, true)
        set(v) = sp.edit { putBoolean(K_FLASH, v) }

    var vibrationMode: Boolean
        get() = sp.getBoolean(K_VIBRATION, true)
        set(v) = sp.edit { putBoolean(K_VIBRATION, v) }

    /** 0..100 */
    var volume: Int
        get() = sp.getInt(K_VOLUME, 80)
        set(v) = sp.edit { putInt(K_VOLUME, v.coerceIn(0, 100)) }

    /** Alert length in seconds: 5 / 15 / 30 / 60 */
    var durationSec: Int
        get() = sp.getInt(K_DURATION, 5)
        set(v) = sp.edit { putInt(K_DURATION, v) }

    var soundId: Int
        get() = sp.getInt(K_SOUND, 0)
        set(v) = sp.edit { putInt(K_SOUND, v) }

    /** 0 = very high, 1 = high, 2 = average */
    var sensitivity: Int
        get() = sp.getInt(K_SENSITIVITY, 0)
        set(v) = sp.edit { putInt(K_SENSITIVITY, v) }

    var scheduleEnabled: Boolean
        get() = sp.getBoolean(K_SCHED, false)
        set(v) = sp.edit { putBoolean(K_SCHED, v) }

    /** minutes-from-midnight */
    var scheduleStart: Int
        get() = sp.getInt(K_SCHED_START, 0)          // 00:00
        set(v) = sp.edit { putInt(K_SCHED_START, v) }

    var scheduleEnd: Int
        get() = sp.getInt(K_SCHED_END, 6 * 60)       // 06:00
        set(v) = sp.edit { putInt(K_SCHED_END, v) }

    var serviceRunning: Boolean
        get() = sp.getBoolean(K_RUNNING, false)
        set(v) = sp.edit { putBoolean(K_RUNNING, v) }

    /* ===== Whistle Finder ===== */
    var whistleServiceRunning: Boolean
        get() = sp.getBoolean(K_WHISTLE_RUNNING, false)
        set(v) = sp.edit { putBoolean(K_WHISTLE_RUNNING, v) }

    /** 1..10 */
    var whistleSensitivity: Int
        get() = sp.getInt(K_WHISTLE_SENSITIVITY, 5)
        set(v) = sp.edit { putInt(K_WHISTLE_SENSITIVITY, v.coerceIn(1, 10)) }

    /* ===== Push notifications ===== */
    /** True once Home has asked for POST_NOTIFICATIONS (Android 13+), so it asks only once. */
    var notificationPromptShown: Boolean
        get() = sp.getBoolean(K_NOTIF_PROMPT, false)
        set(v) = sp.edit { putBoolean(K_NOTIF_PROMPT, v) }

    companion object {
        private const val K_ONBOARDING = "onboarding_done"
        private const val K_FLASH = "flash_mode"
        private const val K_VIBRATION = "vibration_mode"
        private const val K_VOLUME = "volume"
        private const val K_DURATION = "duration_sec"
        private const val K_SOUND = "sound_id"
        private const val K_SENSITIVITY = "sensitivity"
        private const val K_SCHED = "schedule_enabled"
        private const val K_SCHED_START = "schedule_start"
        private const val K_SCHED_END = "schedule_end"
        private const val K_RUNNING = "service_running"

        private const val K_WHISTLE_RUNNING = "whistle_service_running"
        private const val K_WHISTLE_SENSITIVITY = "whistle_sensitivity"
        private const val K_NOTIF_PROMPT = "notification_prompt_shown"

        @Volatile private var instance: Prefs? = null
        fun get(ctx: Context): Prefs =
            instance ?: synchronized(this) { instance ?: Prefs(ctx).also { instance = it } }
    }
}