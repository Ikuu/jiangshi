package yokai.domain.stats

import eu.kanade.tachiyomi.domain.manga.models.Manga

/** The three kinds of reading the stats compare. Manhwa and manhua are almost always long strips, so count as webtoons. */
enum class ReadingCategory {
    COMIC,
    MANGA,
    WEBTOON,
    ;

    companion object {
        fun of(seriesType: Int): ReadingCategory = when (seriesType) {
            Manga.TYPE_COMIC -> COMIC
            Manga.TYPE_WEBTOON, Manga.TYPE_MANHWA, Manga.TYPE_MANHUA -> WEBTOON
            else -> MANGA
        }
    }
}
