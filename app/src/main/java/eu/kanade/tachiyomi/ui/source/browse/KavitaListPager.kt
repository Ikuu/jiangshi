package eu.kanade.tachiyomi.ui.source.browse

import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.kavita.KavitaApi
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.sourcePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** The lists of series a Kavita source can show from its server, alongside Popular and Latest. */
enum class KavitaList(val fetch: suspend KavitaApi.(apiUrl: String, apiKey: String, page: Int) -> MangasPage) {
    /** Series partway through on the server. */
    ON_DECK({ apiUrl, apiKey, page -> getOnDeck(apiUrl, apiKey, page) }),

    /** Series on the user's Want to Read list. */
    WANT_TO_READ({ apiUrl, apiKey, page -> getWantToRead(apiUrl, apiKey, page) }),
}

/** One of Kavita's lists, from the server that this Kavita source points at. */
class KavitaListPager(private val source: CatalogueSource, private val list: KavitaList) : Pager() {

    /** The server's page, which runs ahead of [currentPage] when pages of only EPUBs are skipped */
    private var serverPage = 1

    override suspend fun requestNextPage() {
        val preferences = (source as ConfigurableSource).sourcePreferences()
        val apiUrl = preferences.getString("APIURL", "").orEmpty()
        val apiKey = preferences.getString("APIKEY", "").orEmpty()
        val api = Injekt.get<TrackManager>().kavita.api
        var page = list.fetch(api, apiUrl, apiKey, serverPage++)
        while (page.mangas.isEmpty() && page.hasNextPage) {
            page = list.fetch(api, apiUrl, apiKey, serverPage++)
        }
        onPageReceived(page)
    }
}
