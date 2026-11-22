package eu.kanade.tachiyomi.data.stats

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.notification
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.workManager
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.stats.Streak
import yokai.domain.stats.StreakTier
import yokai.domain.stats.interactor.GetReadingSessions
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Runs each evening at the time set, and posts a reminder if there's a reading streak going and nothing has
 * been read yet today. Each run schedules the next one, so the time follows the device's clock and zone.
 */
class StreakReminderJob(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val preferences = Injekt.get<PreferencesHelper>()
        if (!preferences.streakReminder().get()) return Result.success()
        try {
            val sessions = Injekt.get<GetReadingSessions>().awaitAll()
            Streak.atRisk(sessions, Instant.now(), ZoneId.systemDefault())?.let { notify(applicationContext, it) }
        } catch (e: Exception) {
            Logger.e(e) { "Could not check the reading streak" }
        }
        // A run that starts a moment early mustn't schedule itself again for the same evening
        setupTask(applicationContext, after = ZonedDateTime.now().plusMinutes(5))
        return Result.success()
    }

    companion object {
        private const val TAG = "StreakReminder"

        /** Schedules the next reminder, or cancels it when reminders are off. */
        fun setupTask(context: Context, after: ZonedDateTime = ZonedDateTime.now()) {
            val preferences = Injekt.get<PreferencesHelper>()
            if (!preferences.streakReminder().get()) {
                context.workManager.cancelUniqueWork(TAG)
                return
            }
            val delay = Duration.between(ZonedDateTime.now(), nextRun(after, preferences.streakReminderHour().get()))
            val request = OneTimeWorkRequestBuilder<StreakReminderJob>()
                .setInitialDelay(delay.toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        /** The first time the clock reads [hour]:00 after [after]. */
        internal fun nextRun(after: ZonedDateTime, hour: Int): ZonedDateTime {
            val today = after.toLocalDate().atTime(LocalTime.of(hour, 0)).atZone(after.zone)
            return if (today.isAfter(after)) today else today.plusDays(1)
        }

        /** Takes the reminder away, such as once something has been read. */
        fun dismiss(context: Context) {
            context.notificationManager.cancel(Notifications.ID_STREAK_REMINDER)
        }

        private fun notify(context: Context, streak: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val tier = StreakTier.of(streak) ?: return
            val localized = context.localeContext
            val openStats = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.SHORTCUT_STATS)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = localized.notification(Notifications.CHANNEL_REMINDERS) {
                setSmallIcon(R.drawable.ic_yokai)
                setContentTitle(localized.getString(MR.plurals.streak_reminder_title, streak, tier.emoji, streak))
                setContentText(localized.getString(MR.strings.streak_reminder_body))
                setContentIntent(openStats)
                setAutoCancel(true)
            }
            context.notificationManager.notify(Notifications.ID_STREAK_REMINDER, notification)
        }
    }
}
