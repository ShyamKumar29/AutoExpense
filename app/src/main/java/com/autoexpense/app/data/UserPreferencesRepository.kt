package com.autoexpense.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
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
        private val THEME_KEY = stringPreferencesKey("theme")
        private val BUDGET_WARNING_THRESHOLD_KEY = floatPreferencesKey("budget_warning_threshold")
        private val HAPTIC_FEEDBACK_KEY = booleanPreferencesKey("haptic_feedback")
        private val PAYMENT_SETUP_COMPLETED_KEY = booleanPreferencesKey("payment_setup_completed")

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

    val theme: Flow<String> = context.userDataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: "system"
    }

    val budgetWarningThreshold: Flow<Float> = context.userDataStore.data.map { preferences ->
        preferences[BUDGET_WARNING_THRESHOLD_KEY] ?: 0.7f
    }

    val isHapticFeedbackEnabled: Flow<Boolean> = context.userDataStore.data.map { preferences ->
        preferences[HAPTIC_FEEDBACK_KEY] ?: true
    }

    val isPaymentSetupCompleted: Flow<Boolean> = context.userDataStore.data.map { preferences ->
        preferences[PAYMENT_SETUP_COMPLETED_KEY] ?: false
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

    suspend fun saveTheme(theme: String) {
        context.userDataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }

    suspend fun saveBudgetWarningThreshold(threshold: Float) {
        context.userDataStore.edit { preferences ->
            preferences[BUDGET_WARNING_THRESHOLD_KEY] = threshold
        }
    }

    suspend fun saveHapticFeedback(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[HAPTIC_FEEDBACK_KEY] = enabled
        }
    }

    suspend fun completePaymentSetup() {
        context.userDataStore.edit { preferences ->
            preferences[PAYMENT_SETUP_COMPLETED_KEY] = true
        }
    }

    suspend fun clearAllPreferences() {
        context.userDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
