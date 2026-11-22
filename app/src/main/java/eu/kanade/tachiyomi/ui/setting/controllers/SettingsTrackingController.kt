package eu.kanade.tachiyomi.ui.setting.controllers

import android.app.Activity
import android.content.Context
import eu.kanade.tachiyomi.data.track.DelayedTrackingUpdateJob
import eu.kanade.tachiyomi.data.track.PendingReadMarks
import eu.kanade.tachiyomi.data.track.kavita.KavitaApi
import yokai.domain.chapter.interactor.GetChapter
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.setCustomTitleAndMessage
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.withUIContext
import yokai.domain.manga.interactor.GetManga
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import yokai.util.lang.getString
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackPreferences
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.add
import eu.kanade.tachiyomi.ui.setting.defaultValue
import eu.kanade.tachiyomi.ui.setting.iconRes
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.widget.preference.TrackLoginDialog
import eu.kanade.tachiyomi.widget.preference.TrackLogoutDialog
import eu.kanade.tachiyomi.widget.preference.TrackerPreference
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsTrackingController :
    SettingsLegacyController(),
    TrackLoginDialog.Listener,
    TrackLogoutDialog.Listener {

    private val trackManager: TrackManager by injectLazy()
    val trackPreferences: TrackPreferences by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.tracking

        preference {
            key = "pending_track_updates"
            isPersistent = false
            titleRes = MR.strings.pending_updates
            isVisible = false
            val refresh = {
                val count = preferences.trackingsToAddOnline().get().size + trackManager.suwayomi.allPendingMarks().size +
                    trackManager.suwayomi.allPendingBookmarks().size + trackManager.kavita.allPendingMarks().size
                isVisible = count > 0
                summary = context.getString(MR.plurals.pending_updates_waiting, count, count)
            }
            preferences.trackingsToAddOnline().changesIn(viewScope) { refresh() }
            preferences.suwayomiPendingMarks().changesIn(viewScope) { refresh() }
            preferences.suwayomiPendingBookmarks().changesIn(viewScope) { refresh() }
            preferences.kavitaPendingMarks().changesIn(viewScope) { refresh() }
            onClick { showPendingUpdates() }
        }
        switchPreference {
            key = Keys.autoUpdateTrack
            titleRes = MR.strings.update_tracking_after_reading
            defaultValue = true
        }
        switchPreference {
            key = Keys.trackMarkedAsRead
            titleRes = MR.strings.update_tracking_marked_read
            defaultValue = false
        }
        preferenceCategory {
            titleRes = MR.strings.services

            trackPreference(trackManager.myAnimeList) {
                activity?.openInBrowser(MyAnimeListApi.authUrl(), trackManager.myAnimeList.getLogoColor(), true)
            }
            trackPreference(trackManager.aniList) {
                activity?.openInBrowser(AnilistApi.authUrl(), trackManager.aniList.getLogoColor(), true)
            }
            preference {
                key = "update_anilist_scoring"
                isPersistent = false
                isIconSpaceReserved = true
                title = context.getString(MR.strings.update_tracking_scoring_type, context.getString(MR.strings.anilist))

                preferences.getStringPref(trackManager.aniList.getUsername())
                    .changesIn(viewScope) {
                        isVisible = it.isNotEmpty()
                    }

                onClick {
                    viewScope.launchIO {
                        val (result, error) = trackManager.aniList.updatingScoring()
                        if (result) {
                            view?.snack(MR.strings.scoring_type_updated)
                        } else {
                            view?.snack(
                                context.getString(
                                    MR.strings.could_not_update_scoring_,
                                    error?.localizedMessage.orEmpty(),
                                ),
                            )
                        }
                    }
                }
            }
            trackPreference(trackManager.kitsu) {
                val dialog = TrackLoginDialog(trackManager.kitsu, MR.strings.email)
                dialog.targetController = this@SettingsTrackingController
                dialog.showDialog(router)
            }
            trackPreference(trackManager.mangaUpdates) {
                val dialog = TrackLoginDialog(trackManager.mangaUpdates, MR.strings.username)
                dialog.targetController = this@SettingsTrackingController
                dialog.showDialog(router)
            }
            trackPreference(trackManager.shikimori) {
                activity?.openInBrowser(ShikimoriApi.authUrl(), trackManager.shikimori.getLogoColor(), true)
            }
            trackPreference(trackManager.bangumi) {
                activity?.openInBrowser(BangumiApi.authUrl(), trackManager.bangumi.getLogoColor(), true)
            }
            infoPreference(MR.strings.tracking_info)
        }
        preferenceCategory {
            titleRes = MR.strings.enhanced_services
            val sourceManager = Injekt.get<SourceManager>()
            val enhancedTrackers = trackManager.services
                .filter { service ->
                    if (service !is EnhancedTrackService) return@filter false
                    sourceManager.getCatalogueSources().any { service.accept(it) }
                }
            enhancedTrackers.forEach { trackPreference(it) }
            infoPreference(MR.strings.enhanced_tracking_info)
        }
    }

    /** An update waiting to be sent, with the text it's listed under. */
    private sealed class PendingUpdate(val label: String) {
        class Tracker(label: String, val entry: String) : PendingUpdate(label)
        class SuwayomiMark(label: String, val mark: Suwayomi.PendingMark) : PendingUpdate(label)
        class SuwayomiBookmark(label: String, val mark: Suwayomi.PendingMark) : PendingUpdate(label)
        class KavitaMark(label: String, val mark: PendingReadMarks.Mark) : PendingUpdate(label)
    }

    private suspend fun pendingUpdates(context: Context): List<PendingUpdate> {
        val getManga: GetManga = Injekt.get()
        val titles = mutableMapOf<Long, String>()
        suspend fun title(mangaId: Long) = titles.getOrPut(mangaId) {
            getManga.awaitById(mangaId)?.title ?: context.getString(MR.strings.pending_unknown_series)
        }
        fun number(chapter: Float) = if (chapter % 1f == 0f) chapter.toInt().toString() else chapter.toString()

        val trackerUpdates = preferences.trackingsToAddOnline().get().mapNotNull { entry ->
            val (mangaId, syncId, chapter) = entry.split(":").takeIf { it.size == 3 } ?: return@mapNotNull null
            val service = syncId.toLongOrNull()?.let(trackManager::getService) ?: return@mapNotNull null
            val label = context.getString(
                MR.strings.pending_tracker_update,
                title(mangaId.toLongOrNull() ?: return@mapNotNull null),
                context.getString(service.nameRes()),
                number(chapter.toFloatOrNull() ?: return@mapNotNull null),
            )
            PendingUpdate.Tracker(label, entry)
        }
        val suwayomi = trackManager.suwayomi
        val marks = suwayomi.allPendingMarks().map { mark ->
            val label = context.getString(
                if (mark.read) MR.strings.pending_marked_read else MR.strings.pending_marked_unread,
                title(mark.mangaId),
                context.getString(suwayomi.nameRes()),
                number(mark.chapterNumber),
            )
            PendingUpdate.SuwayomiMark(label, mark)
        }
        val bookmarks = suwayomi.allPendingBookmarks().map { mark ->
            val label = context.getString(
                if (mark.read) MR.strings.pending_bookmarked else MR.strings.pending_unbookmarked,
                title(mark.mangaId),
                context.getString(suwayomi.nameRes()),
                number(mark.chapterNumber),
            )
            PendingUpdate.SuwayomiBookmark(label, mark)
        }
        val kavita = trackManager.kavita
        val getChapter: GetChapter = Injekt.get()
        val kavitaMarks = kavita.allPendingMarks().groupBy { it.mangaId }.flatMap { (mangaId, mangaMarks) ->
            // Kavita marks are kept by Kavita's chapter ID, so the chapter's own name is shown
            val names = getChapter.awaitAll(mangaId, false)
                .mapNotNull { chapter -> KavitaApi.chapterId(chapter.url)?.let { it.toString() to chapter.name } }
                .toMap()
            mangaMarks.map { mark ->
                val label = context.getString(
                    if (mark.read) MR.strings.pending_named_marked_read else MR.strings.pending_named_marked_unread,
                    title(mangaId),
                    context.getString(kavita.nameRes()),
                    names[mark.chapter] ?: "#${mark.chapter}",
                )
                PendingUpdate.KavitaMark(label, mark)
            }
        }
        return (trackerUpdates + marks + bookmarks + kavitaMarks).sortedBy { it.label }
    }

    private fun showPendingUpdates() {
        val activity = activity ?: return
        viewScope.launchUI {
            val updates = withIOContext { pendingUpdates(activity) }
            if (updates.isEmpty()) return@launchUI
            val ticked = BooleanArray(updates.size)
            activity.materialAlertDialog()
                .setCustomTitleAndMessage(MR.strings.pending_updates, activity.getString(MR.strings.pending_updates_info))
                .setMultiChoiceItems(updates.map { it.label }.toTypedArray(), ticked) { _, which, isTicked ->
                    ticked[which] = isTicked
                }
                .setPositiveButton(activity.getString(MR.strings.send_now)) { _, _ -> sendPendingUpdates() }
                .setNeutralButton(activity.getString(MR.strings.discard_ticked)) { _, _ ->
                    discardPendingUpdates(updates.filterIndexed { i, _ -> ticked[i] })
                }
                .setNegativeButton(activity.getString(MR.strings.close), null)
                .show()
        }
    }

    /** Sends everything waiting and then says how it went, rather than leaving it to the background job. */
    private fun sendPendingUpdates() {
        val activity = activity ?: return
        view?.snack(MR.strings.sending_pending_updates)
        viewScope.launchIO {
            val trackersLeft = DelayedTrackingUpdateJob.sendQueued()
            trackManager.suwayomi.sendAllPendingMarks()
            trackManager.kavita.sendAllPendingMarks()
            val left = trackersLeft + trackManager.suwayomi.allPendingMarks().size +
                trackManager.suwayomi.allPendingBookmarks().size + trackManager.kavita.allPendingMarks().size
            withUIContext {
                view?.snack(
                    if (left == 0) {
                        activity.getString(MR.strings.pending_updates_all_sent)
                    } else {
                        activity.getString(MR.plurals.pending_updates_still_waiting, left, left)
                    },
                )
            }
        }
    }

    private fun discardPendingUpdates(updates: List<PendingUpdate>) {
        if (updates.isEmpty()) return
        val trackerEntries = updates.filterIsInstance<PendingUpdate.Tracker>().map { it.entry }.toSet()
        if (trackerEntries.isNotEmpty()) {
            preferences.trackingsToAddOnline().set(preferences.trackingsToAddOnline().get() - trackerEntries)
        }
        val marks = updates.filterIsInstance<PendingUpdate.SuwayomiMark>().map { it.mark }
        if (marks.isNotEmpty()) viewScope.launchIO { trackManager.suwayomi.discardPendingMarks(marks) }
        val bookmarks = updates.filterIsInstance<PendingUpdate.SuwayomiBookmark>().map { it.mark }
        if (bookmarks.isNotEmpty()) viewScope.launchIO { trackManager.suwayomi.discardPendingBookmarks(bookmarks) }
        val kavitaMarks = updates.filterIsInstance<PendingUpdate.KavitaMark>().map { it.mark }
        if (kavitaMarks.isNotEmpty()) viewScope.launchIO { trackManager.kavita.discardPendingMarks(kavitaMarks) }
    }

    private inline fun PreferenceGroup.trackPreference(
        service: TrackService,
        crossinline login: () -> Unit = { },
    ): TrackerPreference {
        return add(
            TrackerPreference(context).apply {
                key = trackPreferences.trackUsername(service).key()
                title = context.getString(service.nameRes())
                iconRes = service.getLogo()
                iconColor = service.getLogoColor()
                onClick {
                    if (service.isLogged) {
                        if (service is EnhancedTrackService) {
                            service.logout()
                            updatePreference(service)
                        } else {
                            val dialog = TrackLogoutDialog(service)
                            dialog.targetController = this@SettingsTrackingController
                            dialog.showDialog(router)
                        }
                    } else {
                        if (service is EnhancedTrackService) {
                            service.loginNoop()
                            updatePreference(service)
                        } else {
                            login()
                        }
                    }
                }
            },
        )
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        updatePreference(trackManager.myAnimeList)
        updatePreference(trackManager.aniList)
        updatePreference(trackManager.shikimori)
        updatePreference(trackManager.bangumi)
    }

    private fun updatePreference(service: TrackService) {
        val pref = findPreference(trackPreferences.trackUsername(service).key()) as? TrackerPreference
        pref?.notifyChanged()
    }

    override fun trackLoginDialogClosed(service: TrackService) {
        updatePreference(service)
    }

    override fun trackLogoutDialogClosed(service: TrackService) {
        updatePreference(service)
    }
}
