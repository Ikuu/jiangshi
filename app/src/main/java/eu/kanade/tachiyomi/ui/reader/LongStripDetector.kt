package eu.kanade.tachiyomi.ui.reader

import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tells webtoons from paged comics by whether each page's art carries on into the next one.
 *
 * Webtoons are one tall strip cut into slices, so the bottom row of a slice nearly matches the top
 * row of the next, while separately drawn pages don't line up. Pages can't be told apart by shape:
 * webtoon slices are often shaped like a printed page.
 */
class LongStripDetector {

    private val edges = mutableMapOf<Int, PageEdges>()
    private var joined = 0
    private var split = 0

    /**
     * Records a loaded [page] and returns whether the chapter reads as a long strip, or null while
     * there isn't enough evidence either way.
     */
    fun add(page: ReaderPage): Boolean? {
        val pageEdges = page.readEdges() ?: return verdict()
        edges[page.index] = pageEdges
        edges[page.index - 1]?.let { count(it.continuesInto(pageEdges)) }
        edges[page.index + 1]?.let { count(pageEdges.continuesInto(it)) }
        return verdict()
    }

    private fun count(continues: Boolean?) {
        when (continues) {
            true -> joined++
            false -> split++
            null -> {}
        }
    }

    // Webtoons have the odd break at a scene change, so neither side needs to be unanimous
    private fun verdict(): Boolean? = when {
        joined >= MIN_JOINED && joined > split * 2 -> true
        split >= MIN_SPLIT && split > joined -> false
        else -> null
    }

    private class PageEdges(val width: Int, val top: IntArray, val bottom: IntArray) {
        /** Whether [below] carries on this page's art, or null when both edges are plain gutter. */
        fun continuesInto(below: PageEdges): Boolean? {
            if (bottom.isPlain() && below.top.isPlain()) return null
            if (width != below.width) return false
            val difference = bottom.indices.sumOf { abs(bottom[it] - below.top[it]) }.toFloat() / width
            return difference < MAX_SEAM_DIFFERENCE
        }

        private fun IntArray.isPlain(): Boolean {
            val mean = average()
            return sqrt(sumOf { (it - mean) * (it - mean) } / size) < MAX_PLAIN_DEVIATION
        }
    }

    /** Luminance of the page's first and last rows, without decoding the rest of it. */
    private fun ReaderPage.readEdges(): PageEdges? {
        return try {
            val decoder = stream?.invoke()?.use {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(it)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(it, false)
                }
            } ?: return null
            try {
                val width = decoder.width
                val top = decoder.readRow(0, width) ?: return null
                val bottom = decoder.readRow(decoder.height - 1, width) ?: return null
                PageEdges(width, top, bottom)
            } finally {
                decoder.recycle()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun BitmapRegionDecoder.readRow(y: Int, width: Int): IntArray? {
        val bitmap = decodeRegion(Rect(0, y, width, y + 1), null) ?: return null
        val pixels = IntArray(width)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, 1)
        bitmap.recycle()
        return IntArray(width) { x ->
            val pixel = pixels[x]
            (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
        }
    }

    companion object {
        /** Stop looking after this many pages, so chapters of plain gutters don't get read in full */
        const val MAX_PAGES = 30

        private const val MIN_JOINED = 2
        private const val MIN_SPLIT = 3

        /** Mean luminance difference along a seam that still counts as the same art */
        private const val MAX_SEAM_DIFFERENCE = 12f

        /** Luminance spread below which an edge row is blank gutter rather than art */
        private const val MAX_PLAIN_DEVIATION = 8.0
    }
}
