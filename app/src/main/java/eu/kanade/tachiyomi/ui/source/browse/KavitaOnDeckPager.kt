package eu.kanade.tachiyomi.ui.source.browse

import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.sourcePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Kavita's On Deck: series partway through on the server that this Kavita source points at. */
class KavitaOnDeckPager(private val source: CatalogueSource) : Pager() {

    /** The server's page, which runs ahead of [currentPage] when pages of only EPUBs are skipped */
    private var serverPage = 1

    override suspend fun requestNextPage() {
        val preferences = (source as ConfigurableSource).sourcePreferences()
        val apiUrl = preferences.getString("APIURL", "").orEmpty()
        val apiKey = preferences.getString("APIKEY", "").orEmpty()
        val api = Injekt.get<TrackManager>().kavita.api
        var page = api.getOnDeck(apiUrl, apiKey, serverPage++)
        while (page.mangas.isEmpty() && page.hasNextPage) {
            page = api.getOnDeck(apiUrl, apiKey, serverPage++)
        }
        onPageReceived(page)
    }
}
