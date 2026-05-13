package dev.dimension.flare.ui.screen.home

import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.ui.screen.settings.SingleChoiceSettingsItem
import kotlinx.collections.immutable.persistentMapOf

@Composable
internal fun MergePolicySettingsItem(
    selected: TimelineMergePolicy,
    onSelected: (TimelineMergePolicy) -> Unit,
    shapes: ListItemShapes,
) {
    SingleChoiceSettingsItem(
        headline = { Text(text = stringResource(id = R.string.tab_settings_merge_policy)) },
        supporting = { Text(text = stringResource(id = R.string.tab_settings_merge_policy_desc)) },
        items =
            persistentMapOf(
                TimelineMergePolicy.Time to stringResource(id = R.string.tab_settings_merge_policy_time),
                TimelineMergePolicy.TimePerPage to stringResource(id = R.string.tab_settings_merge_policy_time_per_page),
                TimelineMergePolicy.Staggered to stringResource(id = R.string.tab_settings_merge_policy_staggered),
            ),
        selected = selected,
        onSelected = onSelected,
        shapes = shapes,
    )
}
