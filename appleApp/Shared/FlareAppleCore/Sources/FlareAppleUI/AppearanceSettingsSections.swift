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
                Toggle(isOn: Binding(get: {
                    appearance.postActionFixedWidth
                }, set: { newValue in
                    presenter.state.updatePostActionFixedWidth(value: newValue)
                })) {
                    Text("post_action_fixed_width", bundle: FlareAppleUILocalization.bundle)
                    Text("post_action_fixed_width_description", bundle: FlareAppleUILocalization.bundle)
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
        }
    }
}

public struct BehaviorSettingsSection<LinkOpenDefaultsLink: View>: View {
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.appSettings) private var appSettings
    private let linkOpenDefaultsLink: () -> LinkOpenDefaultsLink

    public init(@ViewBuilder linkOpenDefaultsLink: @escaping () -> LinkOpenDefaultsLink) {
        self.linkOpenDefaultsLink = linkOpenDefaultsLink
    }

    public var body: some View {
        Section {
            Toggle(isOn: Binding(get: {
                appSettings.refreshHomeTimelineOnLaunch
            }, set: { newValue in
                presenter.state.updateRefreshHomeTimelineOnLaunch(value: newValue)
            })) {
                Text("settings_refresh_home_timeline_on_launch", bundle: FlareAppleUILocalization.bundle)
                Text("settings_refresh_home_timeline_on_launch_description", bundle: FlareAppleUILocalization.bundle)
            }
            Picker(selection: Binding(get: {
                appSettings.homeTimelineAutoRefreshInterval
            }, set: { newValue in
                presenter.state.updateHomeTimelineAutoRefreshInterval(value: newValue)
            })) {
                Text("settings_auto_refresh_disabled", bundle: FlareAppleUILocalization.bundle)
                    .tag(TimelineAutoRefreshInterval.disabled)
                Text("settings_auto_refresh_one_minute", bundle: FlareAppleUILocalization.bundle)
                    .tag(TimelineAutoRefreshInterval.oneMinute)
                Text("settings_auto_refresh_five_minutes", bundle: FlareAppleUILocalization.bundle)
                    .tag(TimelineAutoRefreshInterval.fiveMinutes)
                Text("settings_auto_refresh_fifteen_minutes", bundle: FlareAppleUILocalization.bundle)
                    .tag(TimelineAutoRefreshInterval.fifteenMinutes)
                Text("settings_auto_refresh_thirty_minutes", bundle: FlareAppleUILocalization.bundle)
                    .tag(TimelineAutoRefreshInterval.thirtyMinutes)
                Text("settings_auto_refresh_one_hour", bundle: FlareAppleUILocalization.bundle)
                    .tag(TimelineAutoRefreshInterval.oneHour)
            } label: {
                Text("settings_home_timeline_auto_refresh_interval", bundle: FlareAppleUILocalization.bundle)
                Text("settings_home_timeline_auto_refresh_interval_description", bundle: FlareAppleUILocalization.bundle)
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
            linkOpenDefaultsLink()
        }
    }
}

public extension BehaviorSettingsSection where LinkOpenDefaultsLink == EmptyView {
    init() {
        self.init {
            EmptyView()
        }
    }
}

public struct LinkOpenDefaultsSettingsSection: View {
    @StateObject private var presenter = KotlinPresenter(presenter: LinkOpenDefaultsPresenter())

    public init() {}

    public var body: some View {
        Section {
            StateView(state: presenter.state.targets) { data in
                let targets = data.cast(LinkOpenDefaultsPresenter.Target.self)
                ForEach(targets, id: \.id) { target in
                    LinkOpenDefaultSettingsMenu(
                        target: target,
                        state: presenter.state
                    )
                }
            } loadingContent: {
                EmptyView()
            }
        }
    }
}

private struct LinkOpenDefaultSettingsMenu: View {
    let target: LinkOpenDefaultsPresenter.Target
    let state: LinkOpenDefaultsPresenterState

    var body: some View {
        Menu {
            ForEach(options, id: \.id) { option in
                Toggle(isOn: Binding(get: {
                    target.selectedOption.id == option.id
                }, set: { isSelected in
                    if isSelected {
                        state.select(target: target, option: option)
                    }
                })) {
                    LinkOpenDefaultOptionLabel(option: option)
                }
            }
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(target.title)
                        .foregroundStyle(.primary)
                    LinkOpenDefaultSelectedOptionLabel(option: target.selectedOption)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }

    private var options: [any LinkOpenDefaultsPresenterOption] {
        target.options
    }
}

private struct LinkOpenDefaultSelectedOptionLabel: View {
    let option: any LinkOpenDefaultsPresenterOption

    var body: some View {
        if let account = option.account {
            StateView(state: account.profile) { user in
                Text(user.handle.canonical)
            } errorContent: { error in
                Text(error.message ?? "Unknown error")
            } loadingContent: {
                Text("#loading", bundle: FlareAppleUILocalization.bundle)
            }
        } else {
            LinkOpenDefaultOptionLabel(option: option)
        }
    }
}

private struct LinkOpenDefaultOptionLabel: View {
    let option: any LinkOpenDefaultsPresenterOption

    var body: some View {
        if option.isAsk {
            Text("settings_link_open_default_ask_every_time", bundle: FlareAppleUILocalization.bundle)
        } else if option.isBrowser {
            Text("deep_link_account_picker_open_in_browser", bundle: FlareAppleUILocalization.bundle)
        } else if let account = option.account {
            LinkOpenDefaultAccountRow(account: account)
        }
    }
}

private struct LinkOpenDefaultAccountRow: View {
    let account: LinkOpenDefaultsPresenter.Account

    var body: some View {
        StateView(state: account.profile) { user in
            Label {
                Text(user.handle.canonical)
            } icon: {
                AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
            }
        } errorContent: { error in
            Text(error.message ?? "Unknown error")
        } loadingContent: {
            Text("#loading", bundle: FlareAppleUILocalization.bundle)
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
                    appearance.limitMediaGridToNine
                }, set: { newValue in
                    presenter.state.updateLimitMediaGridToNine(value: newValue)
                })) {
                    Text("appearance_limit_media_grid_to_nine", bundle: FlareAppleUILocalization.bundle)
                    Text("appearance_limit_media_grid_to_nine_description", bundle: FlareAppleUILocalization.bundle)
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
