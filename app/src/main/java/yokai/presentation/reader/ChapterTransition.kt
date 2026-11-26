package yokai.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CardColors
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.pluralStringResource
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.track.LinkedSeries
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.viewer.missingChapterCount
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import kotlinx.collections.immutable.persistentMapOf
import yokai.i18n.MR
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import java.text.NumberFormat
import kotlin.math.max
import yokai.domain.stats.ChapterEndStatus
import yokai.domain.stats.GoalMeasure
import yokai.domain.stats.StreakTier
import yokai.presentation.stats.MILESTONE_EMOJI
import yokai.presentation.stats.formatReadTime
import yokai.presentation.stats.goalPhrase
import yokai.presentation.stats.goalTitle
import yokai.presentation.stats.milestoneText
import yokai.util.secondaryItemAlpha

@Composable
fun ChapterTransition(
    manga: Manga,
    transition: ChapterTransition,
    currChapterDownloaded: Boolean,
    goingToChapterDownloaded: Boolean,
    chapterEnd: ChapterEndStatus? = null,
    linkedNext: LinkedSeries.Next? = null,
    onOpenLinked: (LinkedSeries.Next) -> Unit = {},
) {
    val currChapter = transition.from.chapter
    val goingToChapter = transition.to?.chapter
    val chapterGap = missingChapterCount(transition)

    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
        when (transition) {
            is ChapterTransition.Prev -> {
                TransitionText(
                    manga = manga,
                    topLabel = stringResource(MR.strings.previous_title),
                    topChapter = goingToChapter,
                    topChapterDownloaded = goingToChapterDownloaded,
                    bottomLabel = stringResource(MR.strings.current_chapter),
                    bottomChapter = currChapter,
                    bottomChapterDownloaded = currChapterDownloaded,
                    fallbackLabel = stringResource(MR.strings.theres_no_previous_chapter),
                    chapterGap = chapterGap,
                )
            }
            is ChapterTransition.Next -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TransitionText(
                        manga = manga,
                        topLabel = stringResource(MR.strings.finished_chapter),
                        topChapter = currChapter,
                        topChapterDownloaded = currChapterDownloaded,
                        bottomLabel = stringResource(MR.strings.next_title),
                        bottomChapter = goingToChapter,
                        bottomChapterDownloaded = goingToChapterDownloaded,
                        fallbackLabel = stringResource(MR.strings.theres_no_next_chapter),
                        chapterGap = chapterGap,
                    )
                    if (goingToChapter == null && linkedNext != null) {
                        Spacer(Modifier.height(VerticalSpacerSize))
                        LinkedNextCard(linkedNext, onOpenLinked)
                    }
                    if (chapterEnd != null) {
                        Spacer(Modifier.height(VerticalSpacerSize))
                        ChapterEndCard(chapterEnd, hasNextChapter = goingToChapter != null)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionText(
    manga: Manga,
    topLabel: String,
    topChapter: Chapter?,
    topChapterDownloaded: Boolean,
    bottomLabel: String,
    bottomChapter: Chapter?,
    bottomChapterDownloaded: Boolean,
    fallbackLabel: String,
    chapterGap: Int,
) {
    Column (
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
    ) {
        if (topChapter != null) {
            ChapterText(
                header = topLabel,
                name = topChapter.preferredChapterName(manga),
                scanlator = topChapter.scanlator,
                otherDownloaded = bottomChapterDownloaded,
                downloaded = topChapterDownloaded,
            )

            Spacer(Modifier.height(VerticalSpacerSize))
        } else {
            NoChapterNotification(
                text = fallbackLabel,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        if (bottomChapter != null) {
            if (chapterGap > 0) {
                ChapterGapWarning(
                    gapCount = chapterGap,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Spacer(Modifier.height(VerticalSpacerSize))

            ChapterText(
                header = bottomLabel,
                name = bottomChapter.preferredChapterName(manga),
                scanlator = bottomChapter.scanlator,
                otherDownloaded = topChapterDownloaded,
                downloaded = bottomChapterDownloaded,
            )
        } else {
            NoChapterNotification(
                text = fallbackLabel,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun NoChapterNotification(
    text: String,
    modifier: Modifier = Modifier,
) {
    OutlinedCard (
        modifier = modifier,
        colors = CardColor,
    ) {
        Row (
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null,
            )

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ChapterGapWarning(
    gapCount: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier,
        colors = CardColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
            )

            Text(
                text = pluralStringResource(MR.plurals.missing_chapters_warning, quantity = gapCount, gapCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ChapterHeaderText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun ChapterText(
    header: String,
    name: String,
    scanlator: String?,
    otherDownloaded: Boolean,
    downloaded: Boolean,
) {
    Column {
        ChapterHeaderText(
            text = header,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Text(
            text = buildAnnotatedString {
                if (downloaded || otherDownloaded) {
                    if (downloaded) {
                        appendInlineContent(DOWNLOADED_ICON_ID)
                    } else {
                        appendInlineContent(ONLINE_ICON_ID)
                    }
                    append(' ')
                }
                append(name)
            },
            fontSize = 20.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
            inlineContent = persistentMapOf(
                DOWNLOADED_ICON_ID to InlineTextContent(
                    Placeholder(
                        width = 22.sp,
                        height = 22.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(MR.strings.downloaded),
                    )
                },
                ONLINE_ICON_ID to InlineTextContent(
                    Placeholder(
                        width = 22.sp,
                        height = 22.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = stringResource(MR.strings.not_downloaded),
                    )
                },
            ),
        )

        scanlator?.let {
            Text(
                text = it,
                modifier = Modifier
                    .secondaryItemAlpha()
                    .padding(top = 2.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** The chapter to carry on with in the linked series on the other server, after the last chapter here. */
@Composable
private fun LinkedNextCard(next: LinkedSeries.Next, onOpen: (LinkedSeries.Next) -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
        colors = CardColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChapterHeaderText(stringResource(MR.strings.linked_next_title, stringResource(next.server)))
            Text(
                text = next.chapter.preferredChapterName(next.manga),
                fontSize = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
            )
            FilledTonalButton(
                onClick = { onOpen(next) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(MR.strings.linked_next_read))
            }
        }
    }
}

/** Progress towards the reading goal, the streak, and milestones just reached, after finishing a chapter. */
@Composable
private fun ChapterEndCard(status: ChapterEndStatus, hasNextChapter: Boolean) {
    val context = LocalContext.current
    val numbers = remember { NumberFormat.getIntegerInstance() }
    OutlinedCard(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
        colors = CardColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val progress = status.goal
            val streak = StreakTier.of(status.streak)?.let {
                pluralStringResource(MR.plurals.chapter_end_streak, status.streak, it.emoji, status.streak)
            }
            if (progress != null || streak != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = progress?.let { stringResource(goalTitle(it.goal.period)) }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    streak?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (progress != null) {
                val goal = progress.goal
                val counts = when (goal.measure) {
                    GoalMeasure.MINUTES -> MR.plurals.stats_goal_minutes_of
                    GoalMeasure.CHAPTERS -> MR.plurals.stats_goal_chapters_of
                }
                Text(
                    text = pluralStringResource(counts, goal.target, numbers.format(progress.done), numbers.format(goal.target)),
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 20.sp,
                )
                GoalMeter(progress.fraction)
                val phrase = stringResource(goal.period.goalPhrase())
                val nudge = status.nudge?.takeIf { hasNextChapter }
                val line = when {
                    status.goalJustMet -> stringResource(MR.strings.chapter_end_goal_met)
                    progress.isMet -> stringResource(MR.strings.stats_goal_met)
                    nudge is ChapterEndStatus.Nudge.Chapters -> pluralStringResource(
                        if (nudge.estimated) MR.plurals.chapter_end_nudge_chapters_estimated else MR.plurals.chapter_end_nudge_chapters,
                        nudge.count,
                        nudge.count,
                        phrase,
                    )
                    nudge is ChapterEndStatus.Nudge.Minutes ->
                        pluralStringResource(MR.plurals.chapter_end_nudge_minutes, nudge.minutes, nudge.minutes, phrase)
                    else -> stringResource(
                        MR.strings.stats_goal_to_go,
                        when (goal.measure) {
                            GoalMeasure.MINUTES -> context.formatReadTime(progress.remaining * 60_000L)
                            GoalMeasure.CHAPTERS -> numbers.format(progress.remaining)
                        },
                    )
                }
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (nudge != null || status.goalJustMet) FontWeight.SemiBold else null,
                )
            }
            status.milestones.forEach {
                Text(
                    text = "$MILESTONE_EMOJI ${context.milestoneText(it)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun GoalMeter(fraction: Float) {
    val track = LocalContentColor.current.copy(alpha = 0.2f)
    val fill = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) drawRoundRect(fill, size = Size(max(width, size.height), size.height), cornerRadius = radius)
    }
}

private val CardColor: CardColors
    @Composable
    get() = CardDefaults.outlinedCardColors(
        containerColor = Color.Transparent,
        contentColor = LocalContentColor.current,
    )

private val VerticalSpacerSize = 24.dp
private const val DOWNLOADED_ICON_ID = "downloaded"
private const val ONLINE_ICON_ID = "online"
