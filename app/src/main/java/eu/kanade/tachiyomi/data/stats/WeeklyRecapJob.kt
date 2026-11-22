package eu.kanade.tachiyomi.data.stats

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
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
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.TimeUnit
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.stats.WeeklyRecap
import yokai.domain.stats.interactor.GetReadingSessions
import yokai.i18n.MR
import yokai.presentation.stats.formatReadTime
import yokai.util.lang.getString

/**
 * Runs in the evening on the last day of the week, and posts the week's reading compared with the week
 * before. Weeks start on the locale's first day, as on Statistics. Each run schedules the next one.
 */
class WeeklyRecapJob(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val preferences = Injekt.get<PreferencesHelper>()
        if (!preferences.weeklyRecap().get()) return Result.success()
        try {
            val sessions = Injekt.get<GetReadingSessions>().awaitAll()
            WeeklyRecap.of(sessions, Instant.now(), ZoneId.systemDefault(), firstDayOfWeek())?.let { notify(applicationContext, it) }
        } catch (e: Exception) {
            Logger.e(e) { "Could not put together the weekly recap" }
        }
        // A run that starts a moment early mustn't schedule itself again for the same evening
        setupTask(applicationContext, after = ZonedDateTime.now().plusMinutes(5))
        return Result.success()
    }

    companion object {
        private const val TAG = "WeeklyRecap"
        const val HOUR = 20

        fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

        /** The day the recap comes on: the last day of the week. */
        fun recapDay(): DayOfWeek = firstDayOfWeek().minus(1)

        /** Schedules the next recap, or cancels it when it's off. */
        fun setupTask(context: Context, after: ZonedDateTime = ZonedDateTime.now()) {
            val preferences = Injekt.get<PreferencesHelper>()
            if (!preferences.weeklyRecap().get()) {
                context.workManager.cancelUniqueWork(TAG)
                return
            }
            val delay = Duration.between(ZonedDateTime.now(), nextRun(after, recapDay(), HOUR))
            val request = OneTimeWorkRequestBuilder<WeeklyRecapJob>()
                .setInitialDelay(delay.toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        /** The first time after [after] that it's [day] at [hour]:00. */
        internal fun nextRun(after: ZonedDateTime, day: DayOfWeek, hour: Int): ZonedDateTime {
            val thisWeek = after.toLocalDate().with(TemporalAdjusters.nextOrSame(day)).atTime(LocalTime.of(hour, 0)).atZone(after.zone)
            return if (thisWeek.isAfter(after)) thisWeek else thisWeek.plusWeeks(1)
        }

        private fun notify(context: Context, recap: WeeklyRecap) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val localized = context.localeContext
            val openStats = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.SHORTCUT_STATS)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val difference = recap.time - recap.previousTime
            val comparison = when {
                recap.previousTime <= 0L -> null
                difference >= 60_000L -> localized.getString(MR.strings.weekly_recap_more, localized.formatReadTime(difference))
                difference <= -60_000L -> localized.getString(MR.strings.weekly_recap_less, localized.formatReadTime(-difference))
                else -> localized.getString(MR.strings.weekly_recap_same)
            }
            val body = listOfNotNull(
                localized.getString(MR.plurals.weekly_recap_chapters, recap.chaptersFinished, recap.chaptersFinished),
                localized.getString(MR.plurals.weekly_recap_days, recap.daysRead, recap.daysRead),
                comparison,
            ).joinToString(" · ")
            val notification = localized.notification(Notifications.CHANNEL_REMINDERS) {
                setSmallIcon(R.drawable.ic_yokai)
                setContentTitle(localized.getString(MR.strings.weekly_recap_title, localized.formatReadTime(recap.time)))
                setContentText(body)
                setStyle(NotificationCompat.BigTextStyle().bigText(body))
                setContentIntent(openStats)
                setAutoCancel(true)
            }
            context.notificationManager.notify(Notifications.ID_WEEKLY_RECAP, notification)
        }
    }
}
