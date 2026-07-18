package com.autoexpense.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class PreferencesSnapshot(
    val userName: String = "",
    val isOnboardingCompleted: Boolean = false,
    val theme: String = "system",
    val budgetWarningThreshold: Float = 0.7f,
    val isHapticFeedbackEnabled: Boolean = true,
    val isPaymentSetupCompleted: Boolean = false,
    val isSmartPaymentDetectionEnabled: Boolean = true,
    val isSmartAutoMatchingEnabled: Boolean = true,
    val isSmartAutoMarkPaidEnabled: Boolean = true,
    val isSmartSuggestionsEnabled: Boolean = true,
    val isSmartDashboardWidgetEnabled: Boolean = true
)

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val context: Context) {
    companion object {
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        private val THEME_KEY = stringPreferencesKey("theme")
        private val BUDGET_WARNING_THRESHOLD_KEY = floatPreferencesKey("budget_warning_threshold")
        private val HAPTIC_FEEDBACK_KEY = booleanPreferencesKey("haptic_feedback")
        private val PAYMENT_SETUP_COMPLETED_KEY = booleanPreferencesKey("payment_setup_completed")
        private val LAST_BACKUP_TIMESTAMP_KEY = longPreferencesKey("last_backup_timestamp")
        private val SMART_PAYMENT_DETECTION_KEY = booleanPreferencesKey("smart_payment_detection")
        private val SMART_AUTO_MATCHING_KEY = booleanPreferencesKey("smart_auto_matching")
        private val SMART_AUTO_MARK_PAID_KEY = booleanPreferencesKey("smart_auto_mark_paid")
        private val SMART_SUGGESTIONS_KEY = booleanPreferencesKey("smart_suggestions")
        private val SMART_DASHBOARD_WIDGET_KEY = booleanPreferencesKey("smart_dashboard_widget")

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

    val lastBackupTimestamp: Flow<Long> = context.userDataStore.data.map { preferences ->
        preferences[LAST_BACKUP_TIMESTAMP_KEY] ?: 0L
    }

    val isSmartPaymentDetectionEnabled: Flow<Boolean> = context.userDataStore.data.map { preferences ->
        preferences[SMART_PAYMENT_DETECTION_KEY] ?: true
    }

    val isSmartAutoMatchingEnabled: Flow<Boolean> = context.userDataStore.data.map { preferences ->
        preferences[SMART_AUTO_MATCHING_KEY] ?: true
    }

    val isSmartAutoMarkPaidEnabled: Flow<Boolean> = context.userDataStore.data.map { preferences ->
        preferences[SMART_AUTO_MARK_PAID_KEY] ?: true
    }

    val isSmartSuggestionsEnabled: Flow<Boolean> = context.userDataStore.data.map { preferences ->
        preferences[SMART_SUGGESTIONS_KEY] ?: true
    }

    val isSmartDashboardWidgetEnabled: Flow<Boolean> = context.userDataStore.data.map { preferences ->
        preferences[SMART_DASHBOARD_WIDGET_KEY] ?: true
    }

    suspend fun saveLastBackupTimestamp(timestamp: Long) {
        context.userDataStore.edit { preferences ->
            preferences[LAST_BACKUP_TIMESTAMP_KEY] = timestamp
        }
    }

    suspend fun getPreferencesSnapshot(): PreferencesSnapshot {
        val prefs = context.userDataStore.data.first()
        return PreferencesSnapshot(
            userName = prefs[USER_NAME_KEY] ?: "",
            isOnboardingCompleted = prefs[ONBOARDING_COMPLETED_KEY] ?: false,
            theme = prefs[THEME_KEY] ?: "system",
            budgetWarningThreshold = prefs[BUDGET_WARNING_THRESHOLD_KEY] ?: 0.7f,
            isHapticFeedbackEnabled = prefs[HAPTIC_FEEDBACK_KEY] ?: true,
            isPaymentSetupCompleted = prefs[PAYMENT_SETUP_COMPLETED_KEY] ?: false,
            isSmartPaymentDetectionEnabled = prefs[SMART_PAYMENT_DETECTION_KEY] ?: true,
            isSmartAutoMatchingEnabled = prefs[SMART_AUTO_MATCHING_KEY] ?: true,
            isSmartAutoMarkPaidEnabled = prefs[SMART_AUTO_MARK_PAID_KEY] ?: true,
            isSmartSuggestionsEnabled = prefs[SMART_SUGGESTIONS_KEY] ?: true,
            isSmartDashboardWidgetEnabled = prefs[SMART_DASHBOARD_WIDGET_KEY] ?: true
        )
    }

    suspend fun restorePreferencesSnapshot(snapshot: PreferencesSnapshot) {
        context.userDataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = snapshot.userName
            preferences[ONBOARDING_COMPLETED_KEY] = snapshot.isOnboardingCompleted
            preferences[THEME_KEY] = snapshot.theme
            preferences[BUDGET_WARNING_THRESHOLD_KEY] = snapshot.budgetWarningThreshold
            preferences[HAPTIC_FEEDBACK_KEY] = snapshot.isHapticFeedbackEnabled
            preferences[PAYMENT_SETUP_COMPLETED_KEY] = snapshot.isPaymentSetupCompleted
            preferences[SMART_PAYMENT_DETECTION_KEY] = snapshot.isSmartPaymentDetectionEnabled
            preferences[SMART_AUTO_MATCHING_KEY] = snapshot.isSmartAutoMatchingEnabled
            preferences[SMART_AUTO_MARK_PAID_KEY] = snapshot.isSmartAutoMarkPaidEnabled
            preferences[SMART_SUGGESTIONS_KEY] = snapshot.isSmartSuggestionsEnabled
            preferences[SMART_DASHBOARD_WIDGET_KEY] = snapshot.isSmartDashboardWidgetEnabled
        }
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

    suspend fun saveSmartPaymentDetection(enabled: Boolean) {
        context.userDataStore.edit { preferences -> preferences[SMART_PAYMENT_DETECTION_KEY] = enabled }
    }

    suspend fun saveSmartAutoMatching(enabled: Boolean) {
        context.userDataStore.edit { preferences -> preferences[SMART_AUTO_MATCHING_KEY] = enabled }
    }

    suspend fun saveSmartAutoMarkPaid(enabled: Boolean) {
        context.userDataStore.edit { preferences -> preferences[SMART_AUTO_MARK_PAID_KEY] = enabled }
    }

    suspend fun saveSmartSuggestions(enabled: Boolean) {
        context.userDataStore.edit { preferences -> preferences[SMART_SUGGESTIONS_KEY] = enabled }
    }

    suspend fun saveSmartDashboardWidget(enabled: Boolean) {
        context.userDataStore.edit { preferences -> preferences[SMART_DASHBOARD_WIDGET_KEY] = enabled }
    }

    suspend fun clearAllPreferences() {
        context.userDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
