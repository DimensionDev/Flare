import FlareAppleCore
import KotlinSharedUI
import SwiftUI

public struct AppearanceThemeSettingsSection: View {
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.timelineAppearance) private var timelineAppearance

    public init() {}

    public var body: some View {
        Picker(selection: Binding(get: {
            globalAppearance.theme
        }, set: { newValue in
            presenter.state.updateTheme(value: newValue)
        })) {
            Text("appearance_theme_system", bundle: FlareAppleUILocalization.bundle).tag(Theme.system)
            Text("appearance_theme_light", bundle: FlareAppleUILocalization.bundle).tag(Theme.light)
            Text("appearance_theme_dark", bundle: FlareAppleUILocalization.bundle).tag(Theme.dark)
        } label: {
            Text("appearance_theme", bundle: FlareAppleUILocalization.bundle)
            Text("appearance_theme_description", bundle: FlareAppleUILocalization.bundle)
        }

        Picker(selection: Binding(get: {
            timelineAppearance.avatarShape
        }, set: { newValue in
            presenter.state.updateAvatarShape(value: newValue)
        })) {
            Text("appearance_avatar_shape_circle", bundle: FlareAppleUILocalization.bundle).tag(AvatarShape.circle)
            Text("appearance_avatar_shape_square", bundle: FlareAppleUILocalization.bundle).tag(AvatarShape.square)
        } label: {
            Text("appearance_avatar_shape", bundle: FlareAppleUILocalization.bundle)
            Text("appearance_avatar_shape_description", bundle: FlareAppleUILocalization.bundle)
        }

        #if os(iOS)
        VStack(alignment: .leading) {
            Text("appearance_font_size_diff", bundle: FlareAppleUILocalization.bundle)
            Slider(value: Binding(get: {
                globalAppearance.fontSizeDiff
            }, set: { newValue in
                presenter.state.updateFontScale(fontSizeDiff: newValue)
            }), in: -2...4, step: 1) {
                Text("appearance_font_size_diff", bundle: FlareAppleUILocalization.bundle)
            } minimumValueLabel: {
                Image(systemName: "textformat.size.smaller")
            } maximumValueLabel: {
                Image(systemName: "textformat.size.larger")
            }
        }
        #endif
    }
}

public struct AppearanceLayoutSettingsSection<PostActionLayoutLink: View>: View {
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    #if os(iOS)
    @StateObject private var statusPresenter = KotlinPresenter(presenter: AppearancePresenter())
    @Environment(\.globalAppearance) private var globalAppearance
    #endif
    @Environment(\.timelineAppearance) private var appearance
    private let postActionLayoutLink: () -> PostActionLayoutLink

    public init(@ViewBuilder postActionLayoutLink: @escaping () -> PostActionLayoutLink) {
        self.postActionLayoutLink = postActionLayoutLink
    }

    public var body: some View {
        Section {
            Picker(selection: Binding(get: {
                appearance.timelineDisplayMode
            }, set: { newValue in
                presenter.state.updateTimelineDisplayMode(value: newValue)
            })) {
                Text("appearance_timeline_display_mode_card", bundle: FlareAppleUILocalization.bundle).tag(TimelineDisplayMode.card)
                Text("appearance_timeline_display_mode_plain", bundle: FlareAppleUILocalization.bundle).tag(TimelineDisplayMode.plain)
                Text("appearance_timeline_display_mode_gallery", bundle: FlareAppleUILocalization.bundle).tag(TimelineDisplayMode.gallery)
            } label: {
                Text("appearance_timeline_display_mode", bundle: FlareAppleUILocalization.bundle)
                Text("appearance_timeline_display_mode_description", bundle: FlareAppleUILocalization.bundle)
            }
            #if os(iOS)
            Toggle(isOn: Binding(get: {
                globalAppearance.showBottomBarLabels
            }, set: { newValue in
                presenter.state.updateShowBottomBarLabels(value: newValue)
            })) {
                Text("appearance_show_bottom_bar_labels", bundle: FlareAppleUILocalization.bundle)
                Text("appearance_show_bottom_bar_labels_description", bundle: FlareAppleUILocalization.bundle)
            }
            Toggle(isOn: Binding(get: {
                globalAppearance.deckMode
            }, set: { newValue in
                presenter.state.updateDeckMode(value: newValue)
            })) {
                Text("appearance_deck_mode", bundle: FlareAppleUILocalization.bundle)
                Text("appearance_deck_mode_description", bundle: FlareAppleUILocalization.bundle)
            }
            #endif
        }
        Section {
            #if os(iOS)
            StateView(state: statusPresenter.state.sampleStatus) { status in
                TimelineView(data: status)
            }
            #endif
            Toggle(isOn: Binding(get: {
                appearance.fullWidthPost
            }, set: { newValue in
                presenter.state.updateFullWidthPost(value: newValue)
            })) {
                Text("appearance_fullWidthPost", bundle: FlareAppleUILocalization.bundle)
                Text("appearance_fullWidthPost_description", bundle: FlareAppleUILocalization.bundle)
            }
            Picker(selection: Binding(get: {
                appearance.postActionStyle
            }, set: { newValue in
                presenter.state.updatePostActionStyle(value: newValue)
            })) {
                Text("appearance_post_action_style_hidden", bundle: FlareAppleUILocalization.bundle).tag(PostActionStyle.hidden)
                Text("appearance_post_action_style_left_aligned", bundle: FlareAppleUILocalization.bundle).tag(PostActionStyle.leftAligned)
                Text("appearance_post_action_style_right_aligned", bundle: FlareAppleUILocalization.bundle).tag(PostActionStyle.rightAligned)
                Text("appearance_post_action_style_stretch", bundle: FlareAppleUILocalization.bundle).tag(PostActionStyle.stretch)
            } label: {
                Text("appearance_post_action_style", bundle: FlareAppleUILocalization.bundle)
                Text("appearance_post_action_style_description", bundle: FlareAppleUILocalization.bundle)
            }
            if appearance.postActionStyle != .hidden {
                Toggle(isOn: Binding(get: {
                    appearance.showNumbers
                }, set: { newValue in
                    presenter.state.updateShowNumbers(value: newValue)
                })) {
                    Text("appearance_show_numbers", bundle: FlareAppleUILocalization.bundle)
                    Text("appearance_show_numbers_description", bundle: FlareAppleUILocalization.bundle)
                }
            }
            postActionLayoutLink()
        }
    }
}

public extension AppearanceLayoutSettingsSection where PostActionLayoutLink == EmptyView {
    init() {
        self.init {
            EmptyView()
        }
    }
}

public struct AppearanceDisplaySettingsSection: View {
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    #if os(iOS)
    @StateObject private var statusPresenter = KotlinPresenter(presenter: AppearancePresenter())
    @Environment(\.globalAppearance) private var globalAppearance
    #endif
    @Environment(\.timelineAppearance) private var timelineAppearance

    public init() {}

    public var body: some View {
        Section {
            #if os(iOS)
            StateView(state: statusPresenter.state.sampleStatus) { status in
                TimelineView(data: status)
            }
            #endif
            Toggle(isOn: Binding(get: {
                timelineAppearance.absoluteTimestamp
            }, set: { newValue in
                presenter.state.updateAbsoluteTimestamp(value: newValue)
            })) {
                Text("appearance_absolute_timestamp", bundle: FlareAppleUILocalization.bundle)
                Text("appearance_absolute_timestamp_description", bundle: FlareAppleUILocalization.bundle)
            }
            Toggle(isOn: Binding(get: {
                timelineAppearance.showPlatformLogo
            }, set: { newValue in
                presenter.state.updateShowPlatformLogo(value: newValue)
            })) {
                Text("appearance_show_platform_logo", bundle: FlareAppleUILocalization.bundle)
                Text("appearance_show_platform_logo_description", bundle: FlareAppleUILocalization.bundle)
            }
            Toggle(isOn: Binding(get: {
                timelineAppearance.showLinkPreview
            }, set: { newValue in
                presenter.state.updateShowLinkPreview(value: newValue)
            })) {
                Text("appearance_show_link_preview", bundle: FlareAppleUILocalization.bundle)
                Text("appearance_show_link_preview_description", bundle: FlareAppleUILocalization.bundle)
            }
            if timelineAppearance.showLinkPreview {
                Toggle(isOn: Binding(get: {
                    timelineAppearance.compatLinkPreview
                }, set: { newValue in
                    presenter.state.updateCompatLinkPreview(value: newValue)
                })) {
                    Text("appearance_compat_link_preview", bundle: FlareAppleUILocalization.bundle)
                    Text("appearance_compat_link_preview_description", bundle: FlareAppleUILocalization.bundle)
                }
            }
            #if os(iOS)
            Toggle(isOn: Binding(get: {
                globalAppearance.inAppBrowser
            }, set: { newValue in
                presenter.state.updateInAppBrowser(value: newValue)
            })) {
                Text("appearance_in_app_browser", bundle: FlareAppleUILocalization.bundle)
                Text("appearance_in_app_browser_description", bundle: FlareAppleUILocalization.bundle)
            }
            #endif
        }
    }
}

public struct AppearanceMediaSettingsSection: View {
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    #if os(iOS)
    @StateObject private var statusPresenter = KotlinPresenter(presenter: AppearancePresenter())
    #endif
    @Environment(\.timelineAppearance) private var appearance

    public init() {}

    public var body: some View {
        Section {
            #if os(iOS)
            StateView(state: statusPresenter.state.sampleStatus) { status in
                TimelineView(data: status)
            }
            #endif
            Toggle(isOn: Binding(get: {
                appearance.showMedia
            }, set: { newValue in
                presenter.state.updateShowMedia(value: newValue)
            })) {
                Text("appearance_show_media", bundle: FlareAppleUILocalization.bundle)
                Text("appearance_show_media_description", bundle: FlareAppleUILocalization.bundle)
            }
            if appearance.showMedia {
                Toggle(isOn: Binding(get: {
                    appearance.expandMediaSize
                }, set: { newValue in
                    presenter.state.updateExpandMediaSize(value: newValue)
                })) {
                    Text("appearance_expand_media_size", bundle: FlareAppleUILocalization.bundle)
                    Text("appearance_expand_media_size_description", bundle: FlareAppleUILocalization.bundle)
                }
                Toggle(isOn: Binding(get: {
                    appearance.showSensitiveContent
                }, set: { newValue in
                    presenter.state.updateShowSensitiveContent(value: newValue)
                })) {
                    Text("appearance_show_sensitive_content", bundle: FlareAppleUILocalization.bundle)
                    Text("appearance_show_sensitive_content_description", bundle: FlareAppleUILocalization.bundle)
                }
                Toggle(isOn: Binding(get: {
                    appearance.expandContentWarning
                }, set: { newValue in
                    presenter.state.updateExpandContentWarning(value: newValue)
                })) {
                    Text("appearance_expand_content_warning", bundle: FlareAppleUILocalization.bundle)
                    Text("appearance_expand_content_warning_description", bundle: FlareAppleUILocalization.bundle)
                }
                Picker(selection: Binding(get: {
                    appearance.videoAutoplay
                }, set: { newValue in
                    presenter.state.updateVideoAutoplay(value: newValue)
                })) {
                    Text("appearance_video_autoplay_never", bundle: FlareAppleUILocalization.bundle).tag(VideoAutoplay.never)
                    Text("appearance_video_autoplay_wifi", bundle: FlareAppleUILocalization.bundle).tag(VideoAutoplay.wifi)
                    Text("appearance_video_autoplay_always", bundle: FlareAppleUILocalization.bundle).tag(VideoAutoplay.always)
                } label: {
                    Text("appearance_video_autoplay", bundle: FlareAppleUILocalization.bundle)
                    Text("appearance_video_autoplay_description", bundle: FlareAppleUILocalization.bundle)
                }
            }
        }
    }
}
