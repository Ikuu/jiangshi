package eu.kanade.tachiyomi.ui.manga.track

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.suwayomi.ServerTrackerInfo

data class TrackItem(
    val track: Track?,
    val service: TrackService,
    /** Trackers linked on the Suwayomi server, for its tracker only. */
    val serverTrackers: List<ServerTrackerInfo> = emptyList(),
)
