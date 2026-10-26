package se.oscarb.bdai.notifications

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

interface BirthdayNotificationScheduler {
    fun setNotificationsEnabled(enabled: Boolean)
    suspend fun checkAndNotifyToday()
}

/**
 * Returns today's [LocalDate] in the user's current system timezone.
 */
expect fun currentLocalDate(): LocalDate

/**
 * Returns a lambda that checks/requests POST_NOTIFICATIONS permission on Android 13+ and invokes [onResult].
 */
@Composable
expect fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit
