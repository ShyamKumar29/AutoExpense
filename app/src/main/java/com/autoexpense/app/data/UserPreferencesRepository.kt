package com.autoexpense.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val context: Context) {
    companion object {
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")

        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = UserPreferencesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    val userName: Flow<String> = context.userDataStore.data.map { preferences ->
        preferences[USER_NAME_KEY] ?: ""
    }

    val isOnboardingCompleted: Flow<Boolean> = context.userDataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    suspend fun saveUserName(name: String) {
        context.userDataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = name.trim()
        }
    }

    suspend fun completeOnboarding(name: String) {
        context.userDataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = if (name.isNotBlank()) name.trim() else "User"
            preferences[ONBOARDING_COMPLETED_KEY] = true
        }
    }
}
