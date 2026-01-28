import SwiftUI
import KotlinSharedUI

struct AppearanceScreen: View {
    @AppStorage("pref_timeline_use_compose_view") private var useComposeView: Bool = false
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
                        Text("appearance_theme_description")
                    }
                    VStack(
                        alignment: .leading,
                        
                    ) {
                        Text("appearance_font_size_diff")
                        Slider(value: Binding(get: {
                            appearance.fontSizeDiff
                        }, set: { newValue in
                            presenter.state.updateAppearanceSettings { settings in
                                settings.copy(fontSizeDiff: newValue)
                            }
                        }), in: -2...4, step: 1) {
                            Text("appearance_font_size_diff")
                        } minimumValueLabel: {
                            Image(systemName: "textformat.size.smaller")
                        } maximumValueLabel: {
                            Image(systemName: "textformat.size.larger")
                        }
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
                        Text("appearance_avatar_shape_description")
                    }
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

                    Toggle(isOn: Binding(get: {
                        appearance.absoluteTimestamp
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(absoluteTimestamp: newValue)
                        }
                    })) {
                        Text("appearance_absolute_timestamp")
                        Text("appearance_absolute_timestamp_description")
                    }
                    
                    Toggle(isOn: Binding(get: {
                        appearance.showPlatformLogo
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(showPlatformLogo: newValue)
                        }
                    })) {
                        Text("appearance_show_platform_logo")
                        Text("appearance_show_platform_logo_description")
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
                    Toggle(isOn: Binding(get: {
                        appearance.showLinkPreview
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(showLinkPreview: newValue)
                        }
                    })) {
                        Text("appearance_show_link_preview")
                        Text("appearance_show_link_preview_description")
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
                            Text("appearance_compat_link_preview_description")
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
        .navigationTitle("appearance_title")
    }
}

extension AppearanceSettings {
    func copy(
        theme: Theme? = nil,
        dynamicTheme: Bool? = nil,
        colorSeed: UInt64? = nil,
        avatarShape: AvatarShape? = nil,
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
        showComposeInHomeTimeline: Bool? = nil,
        bottomBarStyle: BottomBarStyle? = nil,
        bottomBarBehavior: BottomBarBehavior? = nil,
        inAppBrowser: Bool? = nil,
        fullWidthPost: Bool? = nil,
        postActionStyle: PostActionStyle? = nil,
        absoluteTimestamp: Bool? = nil,
        showPlatformLogo: Bool? = nil,
    ) -> AppearanceSettings {
        AppearanceSettings(
            theme: theme ?? self.theme,
            dynamicTheme: dynamicTheme ?? self.dynamicTheme,
            colorSeed: colorSeed ?? self.colorSeed,
            avatarShape: avatarShape ?? self.avatarShape,
            showActions: false,
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
            showComposeInHomeTimeline: showComposeInHomeTimeline ?? self.showComposeInHomeTimeline,
            bottomBarStyle: bottomBarStyle ?? self.bottomBarStyle,
            bottomBarBehavior: bottomBarBehavior ?? self.bottomBarBehavior,
            inAppBrowser: inAppBrowser ?? self.inAppBrowser,
            fullWidthPost: fullWidthPost ?? self.fullWidthPost,
            postActionStyle: postActionStyle ?? self.postActionStyle,
            absoluteTimestamp: absoluteTimestamp ?? self.absoluteTimestamp,
            showPlatformLogo: showPlatformLogo ?? self.showPlatformLogo
        )
    }
}
