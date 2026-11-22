package yokai.presentation.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BaseComposeController
import eu.kanade.tachiyomi.util.view.activityBinding

/** Statistics, as a tab of the main navigation. */
class StatsController : BaseComposeController() {
    @Composable
    override fun ScreenContent() {
        // As a tab there's nothing to go back to, and the bottom navigation sits over the end of the screen
        val isTab = router.backstack.firstOrNull()?.controller === this
        val bottomNavHeight = with(LocalDensity.current) { (activityBinding?.bottomNav?.height ?: 0).toDp() }
        CompositionLocalProvider(LocalStatsTab provides if (isTab) StatsTab(bottomNavHeight) else null) {
            Navigator(screen = StatsScreen())
        }
    }
}
