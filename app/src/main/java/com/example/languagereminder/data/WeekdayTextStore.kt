package com.example.languagereminder.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.dataStore by preferencesDataStore(name = "weekday_texts")

class WeekdayTextStore(private val context: Context) {

    val weekdayTexts: Flow<Map<DayOfWeek, String>> =
        context.dataStore.data.map { prefs -> readMap(prefs) }

    suspend fun saveWeekdayText(day: DayOfWeek, text: String) {
        context.dataStore.edit { prefs ->
            prefs[day.toKey()] = text
        }
    }

    suspend fun saveAll(values: Map<DayOfWeek, String>) {
        context.dataStore.edit { prefs ->
            DayOfWeek.entries.forEach { day ->
                prefs[day.toKey()] = values[day].orEmpty()
            }
        }
    }

    private fun readMap(prefs: Preferences): Map<DayOfWeek, String> {
        return DayOfWeek.entries.associateWith { day ->
            prefs[day.toKey()] ?: defaultText(day)
        }
    }

    private fun DayOfWeek.toKey() = stringPreferencesKey("weekday_${name.lowercase()}")

    companion object {
        fun defaultText(day: DayOfWeek): String = "Happy ${day.name.lowercase().replaceFirstChar { it.uppercase() }}"
    }
}
