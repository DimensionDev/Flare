import SwiftUI
import KotlinSharedUI

struct AppearanceLayoutScreen: View {
    @AppStorage("pref_timeline_use_compose_view") private var useComposeView: Bool = false
    @StateObject private var statusPresenter = KotlinPresenter(presenter: AppearancePresenter())
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    var body: some View {
        List {
            Section {
                StateView(state: presenter.state.appearance) { appearance in
                    Picker(selection: Binding(get: {
                        appearance.timelineDisplayMode
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(timelineDisplayMode: newValue)
                        }
                    })) {
                        Text("appearance_timeline_display_mode_card").tag(TimelineDisplayMode.card)
                        Text("appearance_timeline_display_mode_plain").tag(TimelineDisplayMode.plain)
                        Text("appearance_timeline_display_mode_gallery").tag(TimelineDisplayMode.gallery)
                    } label: {
                        Text("appearance_timeline_display_mode")
                        Text("appearance_timeline_display_mode_description")
                    }
                    Toggle(isOn: $useComposeView) {
                        Text("appearance_use_compose_view")
                        Text("appearance_use_compose_view_description")
                    }
                }
            }
            Section {
                StateView(state: statusPresenter.state.sampleStatus) { status in
                    TimelineView(data: status)
                }
                StateView(state: presenter.state.appearance) { appearance in
                    Toggle(isOn: Binding(get: {
                        appearance.fullWidthPost
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(fullWidthPost: newValue)
                        }
                    })) {
                        Text("appearance_fullWidthPost")
                        Text("appearance_fullWidthPost_description")
                    }
                    Picker(selection: Binding(get: {
                        appearance.postActionStyle
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(postActionStyle: newValue)
                        }
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
                            presenter.state.updateAppearanceSettings { settings in
                                settings.copy(showNumbers: newValue)
                            }
                        })) {
                            Text("appearance_show_numbers")
                            Text("appearance_show_numbers_description")
                        }
                    }
                }
            }
        }
        .navigationTitle("appearance_layout_group_title")
    }
}
