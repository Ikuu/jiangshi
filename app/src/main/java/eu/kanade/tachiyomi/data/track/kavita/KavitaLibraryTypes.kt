package eu.kanade.tachiyomi.data.track.kavita

import eu.kanade.tachiyomi.core.preference.PreferenceStore
import uy.kohesive.injekt.injectLazy

/**
 * The Kavita library type of each series, keyed by the series' API URL. The extension doesn't put
 * the library type on the series, so it's looked up from the server once and kept here so the
 * library can filter comics without going online.
 */
object KavitaLibraryTypes {

    // REF: https://github.com/Kareadita/Kavita/blob/develop/API/Entities/Enums/LibraryType.cs
    const val COMIC = 1
    const val COMIC_VINE = 5

    private val preferenceStore: PreferenceStore by injectLazy()
    private val preference by lazy { preferenceStore.getStringSet("kavita_series_library_types", emptySet()) }

    @Volatile
    private var types: Map<String, Int>? = null

    private fun types(): Map<String, Int> {
        return types ?: preference.get()
            .mapNotNull { entry ->
                val type = entry.substringBefore('|').toIntOrNull() ?: return@mapNotNull null
                entry.substringAfter('|') to type
            }
            .toMap()
            .also { types = it }
    }

    fun contains(url: String): Boolean = url in types()

    fun isComic(url: String): Boolean = types()[url].let { it == COMIC || it == COMIC_VINE }

    /** Returns true if anything changed */
    @Synchronized
    fun putAll(newTypes: Map<String, Int>): Boolean {
        val merged = types() + newTypes
        if (merged == types()) return false
        preference.set(merged.map { (url, type) -> "$type|$url" }.toSet())
        types = merged
        return true
    }
}
