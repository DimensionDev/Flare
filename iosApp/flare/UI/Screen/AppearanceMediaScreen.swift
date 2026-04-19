import SwiftUI
import KotlinSharedUI

struct AppearanceMediaScreen: View {
    @StateObject private var statusPresenter = KotlinPresenter(presenter: AppearancePresenter())
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    var body: some View {
        List {
            Section {
                StateView(state: statusPresenter.state.sampleStatus) { status in
                    TimelineView(data: status)
                }
                StateView(state: presenter.state.appearance) { appearance in
                    Toggle(isOn: Binding(get: {
                        appearance.showMedia
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(showMedia: newValue)
                        }
                    })) {
                        Text("appearance_show_media")
                        Text("appearance_show_media_description")
                    }
                    if appearance.showMedia {
                        Toggle(isOn: Binding(get: {
                            appearance.expandMediaSize
                        }, set: { newValue in
                            presenter.state.updateAppearanceSettings { settings in
                                settings.copy(expandMediaSize: newValue)
                            }
                        })) {
                            Text("appearance_expand_media_size")
                            Text("appearance_expand_media_size_description")
                        }
                        Toggle(isOn: Binding(get: {
                            appearance.showSensitiveContent
                        }, set: { newValue in
                            presenter.state.updateAppearanceSettings { settings in
                                settings.copy(showSensitiveContent: newValue)
                            }
                        })) {
                            Text("appearance_show_sensitive_content")
                            Text("appearance_show_sensitive_content_description")
                        }
                        Picker(selection: Binding(get: {
                            appearance.videoAutoplay
                        }, set: { newValue in
                            presenter.state.updateAppearanceSettings { settings in
                                settings.copy(videoAutoplay: newValue)
                            }
                        })) {
                            Text("appearance_video_autoplay_never").tag(VideoAutoplay.never)
                            Text("appearance_video_autoplay_wifi").tag(VideoAutoplay.wifi)
                            Text("appearance_video_autoplay_always").tag(VideoAutoplay.always)
                        } label: {
                            Text("appearance_video_autoplay")
                            Text("appearance_video_autoplay_description")
                        }
                    }
                }
            }
        }
        .navigationTitle("appearance_media_group_title")
    }
}
