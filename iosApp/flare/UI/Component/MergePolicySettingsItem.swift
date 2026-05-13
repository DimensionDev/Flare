import SwiftUI
import KotlinSharedUI

struct MergePolicySettingsItem: View {
    @Binding var selected: TimelineMergePolicy

    var body: some View {
        Picker(selection: $selected) {
            Text("tab_settings_merge_policy_time").tag(TimelineMergePolicy.time)
            Text("tab_settings_merge_policy_time_per_page").tag(TimelineMergePolicy.timePerPage)
            Text("tab_settings_merge_policy_staggered").tag(TimelineMergePolicy.staggered)
        } label: {
            Text("tab_settings_merge_policy")
            Text("tab_settings_merge_policy_desc")
        }
    }
}
