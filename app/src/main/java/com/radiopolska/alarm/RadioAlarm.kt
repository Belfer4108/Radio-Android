package com.radiopolska.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.radiopolska.MainActivity
import com.radiopolska.player.RadioPlaybackService
import java.util.Calendar

data class RadioAlarmConfig(
    val enabled: Boolean = false,
    val stationId: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val repeat: Boolean = false,
    val weekdays: Set<Int> = emptySet(),
    val rampVolume: Boolean = true,
    val snoozeMinutes: Int = 10,
    val autoOffMinutes: Int = 60,
    val alarmVolumePercent: Int = 80,
)

object RadioAlarmStore {
    private const val PREFS = "radio_alarm_preferences"
    private const val CONFIG = "config"

    fun load(context: Context): RadioAlarmConfig {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CONFIG, "")
            .orEmpty()
        if (raw.isBlank()) return RadioAlarmConfig()
        val parts = raw.split(";")
        return runCatching {
            RadioAlarmConfig(
                enabled = parts.getOrNull(0).toBooleanCompat(),
                stationId = parts.getOrNull(1).orEmpty(),
                hour = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 23) ?: 7,
                minute = parts.getOrNull(3)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                repeat = parts.getOrNull(4).toBooleanCompat(),
                weekdays = parts.getOrNull(5).orEmpty().split(",").mapNotNull { it.toIntOrNull() }.toSet(),
                rampVolume = parts.getOrNull(6).toBooleanCompat(default = true),
                snoozeMinutes = parts.getOrNull(7)?.toIntOrNull()?.coerceIn(1, 60) ?: 10,
                autoOffMinutes = parts.getOrNull(8)?.toIntOrNull()?.coerceIn(0, 240) ?: 60,
                alarmVolumePercent = parts.getOrNull(9)?.toIntOrNull()?.coerceIn(5, 100) ?: 80,
            )
        }.getOrDefault(RadioAlarmConfig())
    }

    fun save(context: Context, config: RadioAlarmConfig) {
        val raw = listOf(
            config.enabled,
            config.stationId,
            config.hour,
            config.minute,
            config.repeat,
            config.weekdays.sorted().joinToString(","),
            config.rampVolume,
            config.snoozeMinutes,
            config.autoOffMinutes,
            config.alarmVolumePercent,
        ).joinToString(";")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CONFIG, raw)
            .apply()
    }

    private fun String?.toBooleanCompat(default: Boolean = false): Boolean =
        this?.toBooleanStrictOrNull() ?: default
}

object RadioAlarmScheduler {
    private const val TAG = "RadioAlarmScheduler"
    private const val REQUEST_CODE = 4108

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun schedule(context: Context, config: RadioAlarmConfig) {
        cancel(context)
        if (!config.enabled || config.stationId.isBlank()) return
        if (!canScheduleExact(context)) {
            Log.e(TAG, "exact alarm permission missing")
            return
        }
        val triggerAt = nextTriggerMillis(config)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context)
        Log.d(TAG, "schedule: station=${config.stationId}, triggerAt=$triggerAt")
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }

    fun scheduleSnooze(context: Context, minutes: Int) {
        if (!canScheduleExact(context)) {
            Log.e(TAG, "snooze exact alarm permission missing")
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + minutes.coerceIn(1, 60) * 60_000L
        Log.d(TAG, "scheduleSnooze: minutes=$minutes, triggerAt=$triggerAt")
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, RadioAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun nextTriggerMillis(config: RadioAlarmConfig): Long {
        val now = Calendar.getInstance()
        var best: Calendar? = null
        val days = if (config.repeat && config.weekdays.isNotEmpty()) config.weekdays else (1..7).toSet()
        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, config.hour)
                set(Calendar.MINUTE, config.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.before(now)) continue
            val day = candidate.get(Calendar.DAY_OF_WEEK)
            if (day in days) {
                if (best == null || candidate.before(best)) best = candidate
            }
        }
        return (best ?: Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }).timeInMillis
    }
}

class RadioAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val config = RadioAlarmStore.load(context)
        Log.d(TAG, "onReceive: enabled=${config.enabled}, station=${config.stationId}")
        if (!config.enabled || config.stationId.isBlank()) return
        RadioPlaybackService.playAlarm(
            context = context,
            stationId = config.stationId,
            rampVolume = config.rampVolume,
            autoOffMinutes = config.autoOffMinutes,
            alarmVolumePercent = config.alarmVolumePercent,
        )
        MainActivity.openAlarmScreen(context)
        if (config.repeat) {
            RadioAlarmScheduler.schedule(context, config)
        } else {
            RadioAlarmStore.save(context, config.copy(enabled = false))
        }
    }

    private companion object {
        const val TAG = "RadioAlarmReceiver"
    }
}
