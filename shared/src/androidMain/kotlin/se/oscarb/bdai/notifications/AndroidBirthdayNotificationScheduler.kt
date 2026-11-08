package se.oscarb.bdai.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.module
import se.oscarb.bdai.domain.BirthdayRepository
import se.oscarb.bdai.domain.flattenBirthdays
import se.oscarb.bdai.voice.AndroidPermissionBridge
import java.util.Locale
import java.util.concurrent.TimeUnit

actual fun currentLocalDate(): LocalDate {
    val now = java.time.LocalDate.now()
    return LocalDate(now.year, now.monthValue, now.dayOfMonth)
}

private const val PERMISSION_ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
actual fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    return remember(context, onResult) {
        {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                onResult(true)
            } else {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    onResult(true)
                } else {
                    val handler = AndroidPermissionBridge.requestPermissionHandler
                    if (handler != null) {
                        handler(Manifest.permission.POST_NOTIFICATIONS, onResult)
                    } else {
                        (context as? Activity)?.let { activity ->
                            ActivityCompat.requestPermissions(
                                activity,
                                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                1002
                            )
                        }
                        onResult(false)
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberLocalNetworkPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    return remember(context, onResult) {
        {
            if (Build.VERSION.SDK_INT < 37) {
                onResult(true)
            } else {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    PERMISSION_ACCESS_LOCAL_NETWORK
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    onResult(true)
                } else {
                    val handler = AndroidPermissionBridge.requestPermissionHandler
                    if (handler != null) {
                        handler(PERMISSION_ACCESS_LOCAL_NETWORK, onResult)
                    } else {
                        (context as? Activity)?.let { activity ->
                            ActivityCompat.requestPermissions(
                                activity,
                                arrayOf(PERMISSION_ACCESS_LOCAL_NETWORK),
                                1003
                            )
                        }
                        onResult(false)
                    }
                }
            }
        }
    }
}

class AndroidBirthdayNotificationScheduler(
    private val context: Context,
    private val repository: BirthdayRepository
) : BirthdayNotificationScheduler {

    companion object {
        const val CHANNEL_ID = "birthday_reminders"
        const val WORK_NAME = "bdai_daily_birthday_check"
    }

    init {
        ensureNotificationChannel()
    }

    override fun setNotificationsEnabled(enabled: Boolean) {
        ensureNotificationChannel()
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<BirthdayNotificationWorker>(24, TimeUnit.HOURS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun checkAndNotifyToday() {
        ensureNotificationChannel()
        if (!hasNotificationPermission()) return

        val settings = repository.settings.first()
        if (!settings.notificationsEnabled) return

        val today = currentLocalDate()
        val people = repository.getEntriesSnapshot().flattenBirthdays(today)
        val isSwedish = Locale.getDefault().language.startsWith("sv", ignoreCase = true)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notificationManager = NotificationManagerCompat.from(context)

        for (person in people) {
            when {
                person.daysUntilNextBirthday == 0 -> {
                    val title = if (isSwedish) {
                        "🎂 ${person.name} fyller ${person.turningAge} år idag!"
                    } else {
                        "🎂 ${person.name} turns ${person.turningAge} today!"
                    }
                    val body = if (isSwedish) {
                        "Glöm inte att gratulera ${person.name} på födelsedagen!"
                    } else {
                        "Don't forget to wish ${person.name} a happy birthday!"
                    }
                    postNotification(
                        notificationManager = notificationManager,
                        id = person.key.hashCode(),
                        title = title,
                        body = body,
                        pendingIntent = pendingIntent
                    )
                }
                person.daysUntilNextBirthday == 7 && settings.notifyDaysBefore -> {
                    val title = if (isSwedish) {
                        "🎂 ${person.name} fyller ${person.turningAge} år om 7 dagar"
                    } else {
                        "🎂 ${person.name} turns ${person.turningAge} in 7 days"
                    }
                    val body = if (isSwedish) {
                        "Fyller år den ${person.nextBirthday}."
                    } else {
                        "Birthday coming up on ${person.nextBirthday}."
                    }
                    postNotification(
                        notificationManager = notificationManager,
                        id = (person.key + ":7d").hashCode(),
                        title = title,
                        body = body,
                        pendingIntent = pendingIntent
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(
        notificationManager: NotificationManagerCompat,
        id: Int,
        title: String,
        body: String,
        pendingIntent: PendingIntent?
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }
        notificationManager.notify(id, builder.build())
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun ensureNotificationChannel() {
        val isSwedish = Locale.getDefault().language.startsWith("sv", ignoreCase = true)
        val name = if (isSwedish) "Födelsedagspåminnelser" else "Birthday Reminders"
        val descriptionText = if (isSwedish) {
            "Dagliga påminnelser om födelsedagar idag och om 7 dagar"
        } else {
            "Daily notifications for birthdays today and in 7 days"
        }
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }
}

class BirthdayNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val scheduler: BirthdayNotificationScheduler by inject()

    override suspend fun doWork(): Result {
        return try {
            scheduler.checkAndNotifyToday()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

val notificationsPlatformModule = module {
    single<BirthdayNotificationScheduler> {
        AndroidBirthdayNotificationScheduler(
            context = androidContext(),
            repository = get()
        )
    }
}
