import SwiftUI
import KotlinSharedUI

struct AppearanceLayoutScreen: View {
    @StateObject private var statusPresenter = KotlinPresenter(presenter: AppearancePresenter())
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.timelineAppearance) private var appearance
    var body: some View {
        List {
            Section {
                Picker(selection: Binding(get: {
                    appearance.timelineDisplayMode
                }, set: { newValue in
                    presenter.state.updateTimelineDisplayMode(value: newValue)
                })) {
                    Text("appearance_timeline_display_mode_card").tag(TimelineDisplayMode.card)
                    Text("appearance_timeline_display_mode_plain").tag(TimelineDisplayMode.plain)
                    Text("appearance_timeline_display_mode_gallery").tag(TimelineDisplayMode.gallery)
                } label: {
                    Text("appearance_timeline_display_mode")
                    Text("appearance_timeline_display_mode_description")
                }
                Toggle(isOn: Binding(get: {
                    globalAppearance.showBottomBarLabels
                }, set: { newValue in
                    presenter.state.updateShowBottomBarLabels(value: newValue)
                })) {
                    Text("appearance_show_bottom_bar_labels")
                    Text("appearance_show_bottom_bar_labels_description")
                }
                Toggle(isOn: Binding(get: {
                    globalAppearance.deckMode
                }, set: { newValue in
                    presenter.state.updateDeckMode(value: newValue)
                })) {
                    Text("appearance_deck_mode")
                    Text("appearance_deck_mode_description")
                }
            }
            Section {
                StateView(state: statusPresenter.state.sampleStatus) { status in
                    TimelineView(data: status)
                }
                Toggle(isOn: Binding(get: {
                    appearance.fullWidthPost
                }, set: { newValue in
                    presenter.state.updateFullWidthPost(value: newValue)
                })) {
                    Text("appearance_fullWidthPost")
                    Text("appearance_fullWidthPost_description")
                }
                Picker(selection: Binding(get: {
                    appearance.postActionStyle
                }, set: { newValue in
                    presenter.state.updatePostActionStyle(value: newValue)
                })) {
                    Text("appearance_post_action_style_hidden").tag(PostActionStyle.hidden)
                    Text("appearance_post_action_style_left_aligned").tag(PostActionStyle.leftAligned)
                    Text("appearance_post_action_style_right_aligned").tag(PostActionStyle.rightAligned)
                    Text("appearance_post_action_style_stretch").tag(PostActionStyle.stretch)
                } label: {
                    Text("appearance_post_action_style")
                    Text("appearance_post_action_style_description")
                }
                if appearance.postActionStyle != .hidden {
                    Toggle(isOn: Binding(get: {
                        appearance.showNumbers
                    }, set: { newValue in
                        presenter.state.updateShowNumbers(value: newValue)
                    })) {
                        Text("appearance_show_numbers")
                        Text("appearance_show_numbers_description")
                    }
                }
                NavigationLink(value: Route.postActionLayout) {
                    VStack(alignment: .leading) {
                        Text("Customize actions")
                        Text("Choose the order and visibility of post action buttons")
                    }
                }
            }
        }
        .navigationTitle("appearance_layout_group_title")
    }
}
