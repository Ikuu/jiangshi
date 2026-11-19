package yokai.presentation.stats

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BaseComposeController

class StatsController : BaseComposeController() {
    @Composable
    override fun ScreenContent() {
        Navigator(screen = StatsScreen())
    }
}
