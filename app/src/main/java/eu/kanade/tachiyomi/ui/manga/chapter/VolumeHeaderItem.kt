package eu.kanade.tachiyomi.ui.manga.chapter

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import java.text.DecimalFormat
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Separator above a volume's chapters. [volume] is null for chapters that aren't in a volume.
 * [index] keeps headers distinct when a sort splits a volume into more than one run. Tapping it
 * hides the volume's chapters ([collapsed]).
 */
class VolumeHeaderItem(
    val volume: Float?,
    private val index: Int,
    val chapters: List<ChapterItem>,
    private val collapsed: Boolean,
) : AbstractFlexibleItem<VolumeHeaderItem.Holder>() {

    private val readCount = chapters.count { it.read }

    override fun getLayoutRes(): Int = R.layout.chapter_volume_header_item

    override fun isSelectable(): Boolean = false

    override fun isSwipeable(): Boolean = false

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): Holder {
        return Holder(view, adapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        val context = holder.itemView.context
        holder.title.text = context.getString(MR.strings.volume_read_progress, name(context), readCount, chapters.size)
        holder.title.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0,
            0,
            if (collapsed) R.drawable.ic_expand_more_24dp else R.drawable.ic_expand_less_24dp,
            0,
        )
    }

    fun name(context: Context): String {
        return if (volume != null) {
            context.getString(MR.strings.volume_number, DecimalFormat("#.###").format(volume))
        } else {
            context.getString(MR.strings.chapters)
        }
    }

    // Read counts and collapsing are part of equality so the row rebinds when they change
    override fun equals(other: Any?): Boolean {
        return other is VolumeHeaderItem && volume == other.volume && index == other.index &&
            readCount == other.readCount && chapters.size == other.chapters.size && collapsed == other.collapsed
    }

    override fun hashCode(): Int = 31 * volume.hashCode() + index

    class Holder(view: View, adapter: FlexibleAdapter<*>) : FlexibleViewHolder(view, adapter) {
        val title: TextView = view.findViewById(R.id.volume_title)
    }
}
