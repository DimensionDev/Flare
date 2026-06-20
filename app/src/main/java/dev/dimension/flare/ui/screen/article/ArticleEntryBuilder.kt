package dev.dimension.flare.ui.screen.article

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.route.Route

internal fun EntryProviderScope<NavKey>.articleEntryBuilder(
    onBack: () -> Unit,
) {
    entry<Route.Article> { args ->
        ArticleScreen(
            accountType = args.accountType,
            articleKey = args.articleKey,
            onBack = onBack,
        )
    }
}
