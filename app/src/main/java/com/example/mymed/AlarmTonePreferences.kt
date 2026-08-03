package com.example.mymed

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import timber.log.Timber

object AlarmTonePreferences {

    private const val PREFS_NAME = "alarm_tone_prefs"
    private const val KEY_ALARM_TONE_URI = "alarm_tone_uri"

    fun getSavedAlarmToneUri(context: Context): Uri? {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALARM_TONE_URI, null)
        return value?.let(Uri::parse)
    }

    fun getEffectiveAlarmToneUri(context: Context): Uri {
        return getSavedAlarmToneUri(context)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    fun saveAlarmToneUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALARM_TONE_URI, uri?.toString())
            .apply()
        Timber.d("Saved global alarm tone: %s", uri)
    }

    fun resetToSystemDefault(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ALARM_TONE_URI)
            .apply()
        Timber.d("Reset global alarm tone to system default")
    }

    fun isUsingSystemDefault(context: Context): Boolean = getSavedAlarmToneUri(context) == null

    fun getCurrentToneTitle(context: Context): String {
        val effectiveUri = getEffectiveAlarmToneUri(context)
        val customSelected = !isUsingSystemDefault(context)

        return try {
            val title = RingtoneManager.getRingtone(context, effectiveUri)?.getTitle(context)
            when {
                title.isNullOrBlank() && customSelected -> context.getString(R.string.alarm_settings_custom_tone)
                title.isNullOrBlank() -> context.getString(R.string.alarm_settings_system_default)
                customSelected -> title
                else -> context.getString(R.string.alarm_settings_system_default_with_name, title)
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not read alarm tone title")
            if (customSelected) {
                context.getString(R.string.alarm_settings_custom_tone)
            } else {
                context.getString(R.string.alarm_settings_system_default)
            }
        }
    }
}

