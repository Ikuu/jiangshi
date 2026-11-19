package eu.kanade.tachiyomi.ui.source.browse

import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.sourcePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Kavita's On Deck: series partway through on the server that this Kavita source points at. */
class KavitaOnDeckPager(private val source: CatalogueSource) : Pager() {

    override suspend fun requestNextPage() {
        val preferences = (source as ConfigurableSource).sourcePreferences()
        val apiUrl = preferences.getString("APIURL", "").orEmpty()
        val apiKey = preferences.getString("APIKEY", "").orEmpty()
        onPageReceived(Injekt.get<TrackManager>().kavita.api.getOnDeck(apiUrl, apiKey, currentPage))
    }
}
