package dev.dimension.flare.ui.screen.home

import androidx.compose.runtime.Composable
import dev.dimension.flare.Res
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.tab_settings_merge_policy
import dev.dimension.flare.tab_settings_merge_policy_desc
import dev.dimension.flare.tab_settings_merge_policy_staggered
import dev.dimension.flare.tab_settings_merge_policy_time
import dev.dimension.flare.tab_settings_merge_policy_time_per_page
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.DropDownButton
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MergePolicySettingsItem(
    selected: TimelineMergePolicy,
    onSelected: (TimelineMergePolicy) -> Unit,
) {
    val items =
        persistentMapOf(
            TimelineMergePolicy.Time to Res.string.tab_settings_merge_policy_time,
            TimelineMergePolicy.TimePerPage to Res.string.tab_settings_merge_policy_time_per_page,
            TimelineMergePolicy.Staggered to Res.string.tab_settings_merge_policy_staggered,
        )
    CardExpanderItem(
        icon = null,
        heading = { Text(stringResource(Res.string.tab_settings_merge_policy)) },
        caption = { Text(stringResource(Res.string.tab_settings_merge_policy_desc)) },
        trailing = {
            MergePolicyDropdown(
                items = items,
                selected = selected,
                onSelected = onSelected,
            )
        },
    )
}

@Composable
private fun MergePolicyDropdown(
    items: Map<TimelineMergePolicy, StringResource>,
    selected: TimelineMergePolicy,
    onSelected: (TimelineMergePolicy) -> Unit,
) {
    MenuFlyoutContainer(
        flyout = {
            items.forEach { (key, value) ->
                MenuFlyoutItem(
                    onClick = {
                        onSelected(key)
                        isFlyoutVisible = false
                    },
                    text = { Text(stringResource(value)) },
                )
            }
        },
        content = {
            DropDownButton(
                onClick = { isFlyoutVisible = !isFlyoutVisible },
                content = {
                    items[selected]?.let {
                        Text(stringResource(it))
                    }
                },
            )
        },
        adaptivePlacement = true,
        placement = FlyoutPlacement.BottomAlignedEnd,
    )
}
