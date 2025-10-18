import SwiftUI
import KotlinSharedUI

struct AppearanceScreen: View {
    @StateObject private var statusPresenter = KotlinPresenter(presenter: AppearancePresenter())
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    var body: some View {
        List {
            Section {
                StateView(state: presenter.state.appearance) { appearance in
                    Picker(selection: Binding(get: {
                        appearance.theme
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(theme: newValue)
                        }
                    })) {
                        Text("appearance_theme_system").tag(Theme.system)
                        Text("appearance_theme_light").tag(Theme.light)
                        Text("appearance_theme_dark").tag(Theme.dark)
                    } label: {
                        Text("appearance_theme")
                    }
                }
            }
            Section {
                StateView(state: statusPresenter.state.sampleStatus) { status in
                    TimelineView(data: status)
                }
                StateView(state: presenter.state.appearance) { appearance in
                    Picker(selection: Binding(get: {
                        appearance.avatarShape
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(avatarShape: newValue)
                        }
                    })) {
                        Text("appearance_avatar_shape_circle").tag(AvatarShape.circle)
                        Text("appearance_avatar_shape_square").tag(AvatarShape.square)
                    } label: {
                        Text("appearance_avatar_shape")
                    }
                    
                    Toggle(isOn: Binding(get: {
                        appearance.showActions
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(showActions: newValue)
                        }
                    })) {
                        Text("appearance_show_actions")
                    }
                    if appearance.showActions {
                        Toggle(isOn: Binding(get: {
                            appearance.showNumbers
                        }, set: { newValue in
                            presenter.state.updateAppearanceSettings { settings in
                                settings.copy(showNumbers: newValue)
                            }
                        })) {
                            Text("appearance_show_numbers")
                        }
                    }
                    Toggle(isOn: Binding(get: {
                        appearance.showLinkPreview
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(showLinkPreview: newValue)
                        }
                    })) {
                        Text("appearance_show_link_preview")
                    }
                    
                    if appearance.showLinkPreview {
                        Toggle(isOn: Binding(get: {
                            appearance.compatLinkPreview
                        }, set: { newValue in
                            presenter.state.updateAppearanceSettings { settings in
                                settings.copy(compatLinkPreview: newValue)
                            }
                        })) {
                            Text("appearance_compat_link_preview")
                        }
                    }
                    
                    Toggle(isOn: Binding(get: {
                        appearance.showMedia
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(showMedia: newValue)
                        }
                    })) {
                        Text("appearance_show_media")
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
                        }
                        
                        Toggle(isOn: Binding(get: {
                            appearance.showSensitiveContent
                        }, set: { newValue in
                            presenter.state.updateAppearanceSettings { settings in
                                settings.copy(showSensitiveContent: newValue)
                            }
                        })) {
                            Text("appearance_show_sensitive_content")
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
                        }
                    }
                }
            }
        }
        .navigationTitle("appearance_title")
    }
}

extension AppearanceSettings {
    func copy(
        theme: Theme? = nil,
        dynamicTheme: Bool? = nil,
        colorSeed: UInt64? = nil,
        avatarShape: AvatarShape? = nil,
        showActions: Bool? = nil,
        pureColorMode: Bool? = nil,
        showNumbers: Bool? = nil,
        showLinkPreview: Bool? = nil,
        showMedia: Bool? = nil,
        showSensitiveContent: Bool? = nil,
        videoAutoplay: VideoAutoplay? = nil,
        expandMediaSize: Bool? = nil,
        compatLinkPreview: Bool? = nil,
        fontSizeDiff: Float? = nil,
        lineHeightDiff: Float? = nil,
        showComposeInHomeTimeline: Bool? = nil
    ) -> AppearanceSettings {
        AppearanceSettings(
            theme: theme ?? self.theme,
            dynamicTheme: dynamicTheme ?? self.dynamicTheme,
            colorSeed: colorSeed ?? self.colorSeed,
            avatarShape: avatarShape ?? self.avatarShape,
            showActions: showActions ?? self.showActions,
            pureColorMode: pureColorMode ?? self.pureColorMode,
            showNumbers: showNumbers ?? self.showNumbers,
            showLinkPreview: showLinkPreview ?? self.showLinkPreview,
            showMedia: showMedia ?? self.showMedia,
            showSensitiveContent: showSensitiveContent ?? self.showSensitiveContent,
            videoAutoplay: videoAutoplay ?? self.videoAutoplay,
            expandMediaSize: expandMediaSize ?? self.expandMediaSize,
            compatLinkPreview: compatLinkPreview ?? self.compatLinkPreview,
            fontSizeDiff: fontSizeDiff ?? self.fontSizeDiff,
            lineHeightDiff: lineHeightDiff ?? self.lineHeightDiff,
            showComposeInHomeTimeline: showComposeInHomeTimeline ?? self.showComposeInHomeTimeline
        )
    }
}
