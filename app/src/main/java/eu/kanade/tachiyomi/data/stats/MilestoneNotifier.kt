package eu.kanade.tachiyomi.data.stats

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.notification
import eu.kanade.tachiyomi.util.system.notificationManager
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.stats.Milestone
import yokai.domain.stats.Milestones
import yokai.domain.stats.interactor.GetReadingSessions
import yokai.i18n.MR
import yokai.presentation.stats.MILESTONE_EMOJI
import yokai.presentation.stats.milestoneText
import yokai.util.lang.getString

/**
 * Announces reading milestones as they're reached. Milestones are worked out from the reading sessions each time,
 * and the ones already announced are remembered, so each is only announced once, even if the session that
 * reached it is later added to.
 */
object MilestoneNotifier {

    /** Marks a set of announced milestones as started, so an empty set isn't mistaken for a new one. */
    private const val STARTED = "started"

    private val lock = Mutex()

    /** Checks for milestones reached since the last check, such as after saving a reading session. */
    suspend fun check(context: Context) = lock.withLock {
        val preferences = Injekt.get<PreferencesHelper>()
        if (!preferences.milestoneNotifications().get()) return
        val sessions = Injekt.get<GetReadingSessions>().awaitAll()
        val reached = Milestones.calculate(sessions, LocalDate.now(), ZoneId.systemDefault()).reached
        val announced = preferences.milestonesAnnounced().get()
        if (STARTED !in announced) {
            // Milestones from before notifications were turned on aren't news
            preferences.milestonesAnnounced().set(reached.map { it.key }.toSet() + STARTED)
            return
        }
        val new = reached.filter { it.key !in announced }
        if (new.isEmpty()) return
        preferences.milestonesAnnounced().set(announced + new.map { it.key })
        notify(context, new)
    }

    /** Starts afresh when notifications are turned on, so only milestones reached from then on are announced. */
    fun reset() {
        Injekt.get<PreferencesHelper>().milestonesAnnounced().set(emptySet())
    }

    private fun notify(context: Context, milestones: List<Milestone>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val localized = context.localeContext
        val openStats = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.SHORTCUT_STATS)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val first = milestones.first()
        val others = milestones.drop(1).joinToString(", ") { localized.milestoneText(it) }
        val body = listOfNotNull(
            others.takeIf { it.isNotEmpty() }?.let { localized.getString(MR.strings.milestone_notification_also, it) },
            localized.getString(MR.strings.milestone_notification_body),
        ).joinToString(". ")
        val notification = localized.notification(Notifications.CHANNEL_REMINDERS) {
            setSmallIcon(R.drawable.ic_yokai)
            setContentTitle("$MILESTONE_EMOJI ${localized.milestoneText(first)}")
            setContentText(body)
            setStyle(NotificationCompat.BigTextStyle().bigText(body))
            setContentIntent(openStats)
            setAutoCancel(true)
        }
        context.notificationManager.notify(Notifications.ID_MILESTONE, notification)
    }
}
