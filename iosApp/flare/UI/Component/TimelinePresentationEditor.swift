import SwiftUI
import KotlinSharedUI

struct TimelinePresentationEditor: View {
    @Binding var text: String
    @Binding var icon: IconType
    let availableIcons: [IconType]
    let withAvatar: Bool
    let canUseAvatar: Bool
    let onWithAvatarChange: (Bool) -> Void
    @Binding var enabled: Bool
    let showEnabled: Bool
    let timelineAppearance: TimelineAppearance
    @Binding var appearancePatch: AppearancePatch
    let titlePlaceholder: LocalizedStringKey

    @State private var showIconPicker = false

    var body: some View {
        TimelinePresentationHeaderEditor(
            text: $text,
            icon: $icon,
            availableIcons: availableIcons,
            titlePlaceholder: titlePlaceholder
        )

        if canUseAvatar || showEnabled {
            Section {
                if canUseAvatar {
                    Toggle(isOn: Binding(get: {
                        withAvatar
                    }, set: { value in
                        onWithAvatarChange(value)
                    })) {
                        Text("tab_settings_edit_use_avatar")
                    }
                }
                if showEnabled {
                    Toggle(isOn: $enabled) {
                        Text("Enabled")
                        Text("Show this timeline in mixed and group timelines.")
                    }
                }
            }
        }

        Section {
            LayoutAppearanceOverrideGroup(
                timelineAppearance: timelineAppearance,
                appearancePatch: $appearancePatch
            )
            DisplayAppearanceOverrideGroup(
                timelineAppearance: timelineAppearance,
                appearancePatch: $appearancePatch
            )
            MediaAppearanceOverrideGroup(
                timelineAppearance: timelineAppearance,
                appearancePatch: $appearancePatch
            )
            ThemeAppearanceOverrideGroup(
                timelineAppearance: timelineAppearance,
                appearancePatch: $appearancePatch
            )
        } header: {
            Text("appearance_title")
        }
    }
}

private struct TimelinePresentationHeaderEditor: View {
    @Binding var text: String
    @Binding var icon: IconType
    let availableIcons: [IconType]
    let titlePlaceholder: LocalizedStringKey
    @State private var showIconPicker = false

    var body: some View {
        Section {
            HStack(spacing: 12) {
                TabIcon(icon: icon, accountType: AccountType.Guest.shared, size: 36)
                    .onTapGesture {
                        showIconPicker = true
                    }
                TextField(titlePlaceholder, text: $text)
            }
        }
        .sheet(isPresented: $showIconPicker) {
            NavigationStack {
                IconPicker(
                    selectedIcon: icon,
                    availableIcons: availableIcons,
                    onSelect: { icon = $0 }
                )
            }
        }
    }
}

private struct LayoutAppearanceOverrideGroup: View {
    let timelineAppearance: TimelineAppearance
    @Binding var appearancePatch: AppearancePatch
    @State private var expanded = false

    private var overridesEnabled: Bool {
        TimelinePresentationAppearancePatchHelper.shared.layoutOverridesEnabled(patch: appearancePatch)
    }

    var body: some View {
        AppearanceOverrideGroup(
            title: "appearance_layout_group_title",
            subtitle: "appearance_layout_group_subtitle",
            overridesEnabled: overridesEnabled,
            expanded: $expanded,
            onOverridesEnabledChange: { value in
                appearancePatch = value
                    ? TimelinePresentationAppearancePatchHelper.shared.enableLayoutOverrides(
                        patch: appearancePatch,
                        appearance: timelineAppearance
                    )
                    : TimelinePresentationAppearancePatchHelper.shared.disableLayoutOverrides(patch: appearancePatch)
            }
        ) {
            Picker(selection: Binding(get: {
                timelineAppearance.timelineDisplayMode
            }, set: { value in
                appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setTimelineDisplayMode(
                    patch: appearancePatch,
                    value: value
                )
            })) {
                Text("appearance_timeline_display_mode_card").tag(TimelineDisplayMode.card)
                Text("appearance_timeline_display_mode_plain").tag(TimelineDisplayMode.plain)
                Text("appearance_timeline_display_mode_gallery").tag(TimelineDisplayMode.gallery)
            } label: {
                Text("appearance_timeline_display_mode")
                Text("appearance_timeline_display_mode_description")
            }
            Toggle(isOn: Binding(get: {
                timelineAppearance.fullWidthPost
            }, set: { value in
                appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setFullWidthPost(
                    patch: appearancePatch,
                    value: value
                )
            })) {
                Text("appearance_fullWidthPost")
                Text("appearance_fullWidthPost_description")
            }
            Picker(selection: Binding(get: {
                timelineAppearance.postActionStyle
            }, set: { value in
                appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setPostActionStyle(
                    patch: appearancePatch,
                    value: value
                )
            })) {
                Text("appearance_post_action_style_hidden").tag(PostActionStyle.hidden)
                Text("appearance_post_action_style_left_aligned").tag(PostActionStyle.leftAligned)
                Text("appearance_post_action_style_right_aligned").tag(PostActionStyle.rightAligned)
                Text("appearance_post_action_style_stretch").tag(PostActionStyle.stretch)
            } label: {
                Text("appearance_post_action_style")
                Text("appearance_post_action_style_description")
            }
            if timelineAppearance.postActionStyle != .hidden {
                Toggle(isOn: Binding(get: {
                    timelineAppearance.showNumbers
                }, set: { value in
                    appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setShowNumbers(
                        patch: appearancePatch,
                        value: value
                    )
                })) {
                    Text("appearance_show_numbers")
                    Text("appearance_show_numbers_description")
                }
            }
        }
    }
}

private struct DisplayAppearanceOverrideGroup: View {
    let timelineAppearance: TimelineAppearance
    @Binding var appearancePatch: AppearancePatch
    @State private var expanded = false

    private var overridesEnabled: Bool {
        TimelinePresentationAppearancePatchHelper.shared.displayOverridesEnabled(patch: appearancePatch)
    }

    var body: some View {
        AppearanceOverrideGroup(
            title: "appearance_display_group_title",
            subtitle: "appearance_display_group_subtitle",
            overridesEnabled: overridesEnabled,
            expanded: $expanded,
            onOverridesEnabledChange: { value in
                appearancePatch = value
                    ? TimelinePresentationAppearancePatchHelper.shared.enableDisplayOverrides(
                        patch: appearancePatch,
                        appearance: timelineAppearance
                    )
                    : TimelinePresentationAppearancePatchHelper.shared.disableDisplayOverrides(patch: appearancePatch)
            }
        ) {
            Toggle(isOn: Binding(get: {
                timelineAppearance.absoluteTimestamp
            }, set: { value in
                appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setAbsoluteTimestamp(
                    patch: appearancePatch,
                    value: value
                )
            })) {
                Text("appearance_absolute_timestamp")
                Text("appearance_absolute_timestamp_description")
            }
            Toggle(isOn: Binding(get: {
                timelineAppearance.showPlatformLogo
            }, set: { value in
                appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setShowPlatformLogo(
                    patch: appearancePatch,
                    value: value
                )
            })) {
                Text("appearance_show_platform_logo")
                Text("appearance_show_platform_logo_description")
            }
            Toggle(isOn: Binding(get: {
                timelineAppearance.showLinkPreview
            }, set: { value in
                appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setShowLinkPreview(
                    patch: appearancePatch,
                    value: value
                )
            })) {
                Text("appearance_show_link_preview")
                Text("appearance_show_link_preview_description")
            }
            if timelineAppearance.showLinkPreview {
                Toggle(isOn: Binding(get: {
                    timelineAppearance.compatLinkPreview
                }, set: { value in
                    appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setCompatLinkPreview(
                        patch: appearancePatch,
                        value: value
                    )
                })) {
                    Text("appearance_compat_link_preview")
                    Text("appearance_compat_link_preview_description")
                }
            }
        }
    }
}

private struct MediaAppearanceOverrideGroup: View {
    let timelineAppearance: TimelineAppearance
    @Binding var appearancePatch: AppearancePatch
    @State private var expanded = false

    private var overridesEnabled: Bool {
        TimelinePresentationAppearancePatchHelper.shared.mediaOverridesEnabled(patch: appearancePatch)
    }

    var body: some View {
        AppearanceOverrideGroup(
            title: "appearance_media_group_title",
            subtitle: "appearance_media_group_subtitle",
            overridesEnabled: overridesEnabled,
            expanded: $expanded,
            onOverridesEnabledChange: { value in
                appearancePatch = value
                    ? TimelinePresentationAppearancePatchHelper.shared.enableMediaOverrides(
                        patch: appearancePatch,
                        appearance: timelineAppearance
                    )
                    : TimelinePresentationAppearancePatchHelper.shared.disableMediaOverrides(patch: appearancePatch)
            }
        ) {
            Toggle(isOn: Binding(get: {
                timelineAppearance.showMedia
            }, set: { value in
                appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setShowMedia(
                    patch: appearancePatch,
                    value: value
                )
            })) {
                Text("appearance_show_media")
                Text("appearance_show_media_description")
            }
            if timelineAppearance.showMedia {
                Toggle(isOn: Binding(get: {
                    timelineAppearance.expandMediaSize
                }, set: { value in
                    appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setExpandMediaSize(
                        patch: appearancePatch,
                        value: value
                    )
                })) {
                    Text("appearance_expand_media_size")
                    Text("appearance_expand_media_size_description")
                }
                Toggle(isOn: Binding(get: {
                    timelineAppearance.showSensitiveContent
                }, set: { value in
                    appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setShowSensitiveContent(
                        patch: appearancePatch,
                        value: value
                    )
                })) {
                    Text("appearance_show_sensitive_content")
                    Text("appearance_show_sensitive_content_description")
                }
                Picker(selection: Binding(get: {
                    timelineAppearance.videoAutoplay
                }, set: { value in
                    appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setVideoAutoplay(
                        patch: appearancePatch,
                        value: value
                    )
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

private struct ThemeAppearanceOverrideGroup: View {
    let timelineAppearance: TimelineAppearance
    @Binding var appearancePatch: AppearancePatch
    @State private var expanded = false

    private var overridesEnabled: Bool {
        TimelinePresentationAppearancePatchHelper.shared.themeOverridesEnabled(patch: appearancePatch)
    }

    var body: some View {
        AppearanceOverrideGroup(
            title: "appearance_theme_group_title",
            subtitle: "appearance_theme_group_subtitle",
            overridesEnabled: overridesEnabled,
            expanded: $expanded,
            onOverridesEnabledChange: { value in
                appearancePatch = value
                    ? TimelinePresentationAppearancePatchHelper.shared.enableThemeOverrides(
                        patch: appearancePatch,
                        appearance: timelineAppearance
                    )
                    : TimelinePresentationAppearancePatchHelper.shared.disableThemeOverrides(patch: appearancePatch)
            }
        ) {
            Picker(selection: Binding(get: {
                timelineAppearance.avatarShape
            }, set: { value in
                appearancePatch = TimelinePresentationAppearancePatchHelper.shared.setAvatarShape(
                    patch: appearancePatch,
                    value: value
                )
            })) {
                Text("appearance_avatar_shape_circle").tag(AvatarShape.circle)
                Text("appearance_avatar_shape_square").tag(AvatarShape.square)
            } label: {
                Text("appearance_avatar_shape")
                Text("appearance_avatar_shape_description")
            }
        }
    }
}

private struct AppearanceOverrideGroup<Content: View>: View {
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey
    let overridesEnabled: Bool
    @Binding var expanded: Bool
    let onOverridesEnabledChange: (Bool) -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        DisclosureGroup(isExpanded: Binding(get: {
            expanded && overridesEnabled
        }, set: { value in
            expanded = value && overridesEnabled
        })) {
            content()
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Toggle(isOn: Binding(get: {
                    overridesEnabled
                }, set: { value in
                    onOverridesEnabledChange(value)
                    expanded = value
                })) {
                    EmptyView()
                }
                .labelsHidden()
            }
        }
        .onChange(of: overridesEnabled) { _, value in
            withAnimation {
                expanded = value
            }
        }
    }
}
