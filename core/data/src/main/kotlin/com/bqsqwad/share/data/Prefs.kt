package com.bqsqwad.share.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("bq_sqwad_share")

data class UserSettings(
    val ageConfirmed: Boolean,
    val themeMode: String,
    val accent: String,
    val haptics: Boolean,
    val reduceMotion: Boolean,
    val notifications: Boolean,
    val reminderMinutes: Int,
    val adFreeUntil: Long,
    val adFreeRewardCount: Int,
    val premiumUntil: Long,
    val blocked: Set<String>,
    val nicknames: Map<String, String>,
    val lastBackgroundAt: Long,
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val age = booleanPreferencesKey("age")
    private val theme = stringPreferencesKey("theme")
    private val accent = stringPreferencesKey("accent")
    private val haptics = booleanPreferencesKey("haptics")
    private val motion = booleanPreferencesKey("motion")
    private val notif = booleanPreferencesKey("notif")
    private val reminder = longPreferencesKey("reminder")
    private val adFree = longPreferencesKey("adfree")
    private val adFreeRewards = longPreferencesKey("adfree_rewards")
    private val premium = longPreferencesKey("premium")
    private val blocked = stringSetPreferencesKey("blocked")
    private val nick = stringPreferencesKey("nicks")
    private val bg = longPreferencesKey("bg")

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        val nickRaw = p[nick].orEmpty()
        val map = nickRaw.split('|').mapNotNull { row ->
            val i = row.indexOf('=')
            if (i <= 0) null else row.substring(0, i) to row.substring(i + 1)
        }.toMap()
        UserSettings(
            ageConfirmed = p[age] == true,
            themeMode = p[theme] ?: "system",
            accent = p[accent] ?: "royal",
            haptics = p[haptics] != false,
            reduceMotion = p[motion] == true,
            notifications = p[notif] != false,
            reminderMinutes = (p[reminder] ?: (9 * 60L)).toInt(),
            adFreeUntil = p[adFree] ?: 0L,
            adFreeRewardCount = (p[adFreeRewards] ?: 0L).toInt().coerceIn(0, 4),
            premiumUntil = p[premium] ?: 0L,
            blocked = p[blocked] ?: emptySet(),
            nicknames = map,
            lastBackgroundAt = p[bg] ?: 0L,
        )
    }

    suspend fun setAgeConfirmed() = context.dataStore.edit { it[age] = true }
    suspend fun setTheme(mode: String) = context.dataStore.edit { it[theme] = mode }
    suspend fun setAccent(name: String) = context.dataStore.edit { it[accent] = name }
    suspend fun setHaptics(on: Boolean) = context.dataStore.edit { it[haptics] = on }
    suspend fun setReduceMotion(on: Boolean) = context.dataStore.edit { it[motion] = on }
    suspend fun setNotifications(on: Boolean) = context.dataStore.edit { it[notif] = on }
    suspend fun setReminderMinutes(min: Int) = context.dataStore.edit { it[reminder] = min.toLong() }
    suspend fun grantAdFree(until: Long) = context.dataStore.edit { it[adFree] = until; it[adFreeRewards] = 0L }
    suspend fun addAdFreeReward(): Int {
        val updated = context.dataStore.edit { prefs ->
            val next = ((prefs[adFreeRewards] ?: 0L).toInt() + 1).coerceAtMost(5)
            prefs[adFreeRewards] = next.toLong()
        }
        return (updated[adFreeRewards] ?: 0L).toInt()
    }
    suspend fun grantPremium(until: Long) = context.dataStore.edit { it[premium] = until }
    suspend fun block(name: String) = context.dataStore.edit { it[blocked] = (it[blocked] ?: emptySet()) + name }
    suspend fun markBackground(now: Long) = context.dataStore.edit { it[bg] = now }
    suspend fun setNickname(endpoint: String, name: String) = context.dataStore.edit { prefs ->
        val current = prefs[nick].orEmpty().split('|').filter { it.contains('=') && !it.startsWith("$endpoint=") }
        prefs[nick] = (current + "$endpoint=$name").joinToString("|")
    }
}
