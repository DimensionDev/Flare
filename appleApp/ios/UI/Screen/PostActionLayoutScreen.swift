import SwiftUI
import KotlinSharedUI
import AppleFontAwesome

struct PostActionLayoutScreen: View {
    @StateObject private var statusPresenter = KotlinPresenter(presenter: AppearancePresenter())
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    @Environment(\.timelineAppearance) private var timelineAppearance
    @State private var config = PostActionLayoutConfig.companion.Default

    private let placements: [PostActionPlacement] = [.buttonRow, .moreMenu, .hidden]

    var body: some View {
        List {
            Section {
                StateView(state: statusPresenter.state.sampleStatus) { status in
                    TimelineView(data: PostActionLayoutPreviewHelper.shared.withPreviewActions(post: status))
                        .environment(\.timelineAppearance, timelineAppearance.withPostActionLayout(config))
                }
                Toggle(isOn: Binding(get: {
                    config.enabled
                }, set: { enabled in
                    updateConfig(
                        PostActionLayoutHelpers.shared.withEnabled(
                            config: config,
                            enabled: enabled
                        )
                    )
                })) {
                    Text("Customize actions")
                    Text("Choose which actions appear in the row, More menu, or stay hidden")
                }
            }

            if config.enabled {
                ForEach(placements, id: \.name) { placement in
                    actionSection(placement)
                }
            }
        }
        .navigationTitle("Post actions")
        .onAppear {
            config = normalizedTimelineConfig
        }
        .onChange(of: persistedSignature) { _, _ in
            config = normalizedTimelineConfig
        }
    }

    @ViewBuilder
    private func actionSection(_ placement: PostActionPlacement) -> some View {
        let families = families(for: placement)
        Section(placement.title) {
            if families.isEmpty {
                Text("No actions")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(families.enumerated()), id: \.element.name) { index, family in
                    actionRow(family: family, placement: placement, index: index, totalCount: families.count)
                }
                .onMove { source, destination in
                    move(placement: placement, from: source, to: destination)
                }
            }
        }
    }

    private func actionRow(
        family: PostActionFamily,
        placement: PostActionPlacement,
        index: Int,
        totalCount: Int
    ) -> some View {
        HStack(spacing: 12) {
            Image(fontAwesome: family.fontAwesomeIcon)
                .frame(width: 24, height: 24)
                .foregroundStyle(.primary)
            Text(family.title)
            Spacer()
            Image(systemName: "line.3.horizontal")
                .foregroundStyle(.secondary)
            Menu {
                ForEach(placements, id: \.name) { target in
                    if target.name != placement.name {
                        Button(target.moveTitle) {
                            updateConfig(
                                PostActionLayoutHelpers.shared.moveTo(
                                    config: config,
                                    family: family,
                                    placement: target
                                )
                            )
                        }
                    }
                }
                Divider()
                Button("Move up") {
                    updateConfig(
                        PostActionLayoutHelpers.shared.moveBy(
                            config: config,
                            family: family,
                            offset: Int32(-1)
                        )
                    )
                }
                .disabled(index == 0)
                Button("Move down") {
                    updateConfig(
                        PostActionLayoutHelpers.shared.moveBy(
                            config: config,
                            family: family,
                            offset: Int32(1)
                        )
                    )
                }
                .disabled(index >= totalCount - 1)
            } label: {
                Image(fontAwesome: .ellipsisVertical)
                    .frame(width: 32, height: 32)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(.secondary)
        }
    }

    private var persistedSignature: String {
        PostActionLayoutHelpers.shared.signature(config: timelineAppearance.postActionLayout)
    }

    private var normalizedTimelineConfig: PostActionLayoutConfig {
        PostActionLayoutHelpers.shared.normalizedForEdit(config: timelineAppearance.postActionLayout)
    }

    private func families(for placement: PostActionPlacement) -> [PostActionFamily] {
        castPostActionFamilies(
            PostActionLayoutHelpers.shared.familiesFor(
                config: config,
                placement: placement
            )
        )
    }

    private func updateConfig(_ value: PostActionLayoutConfig) {
        let normalized = PostActionLayoutHelpers.shared.normalizedForEdit(config: value)
        config = normalized
        presenter.state.updatePostActionLayout(value: normalized)
    }

    private func move(placement: PostActionPlacement, from source: IndexSet, to destination: Int) {
        guard let fromIndex = source.first else { return }
        updateConfig(
            PostActionLayoutHelpers.shared.moveAt(
                config: config,
                placement: placement,
                fromIndex: Int32(fromIndex),
                toOffset: Int32(destination)
            )
        )
    }
}

private extension PostActionPlacement {
    var title: String {
        switch self {
        case .buttonRow: return String(localized: "Button row")
        case .moreMenu: return String(localized: "More menu")
        case .hidden: return String(localized: "Hidden")
        }
    }

    var moveTitle: String {
        switch self {
        case .buttonRow: return String(localized: "Move to button row")
        case .moreMenu: return String(localized: "Move to More menu")
        case .hidden: return String(localized: "Hide action")
        }
    }
}

private extension PostActionFamily {
    var title: String {
        switch self {
        case .reply: return String(localized: "Reply")
        case .comment: return String(localized: "Comment")
        case .repost: return String(localized: "Repost")
        case .quote: return String(localized: "Quote")
        case .like: return String(localized: "Like")
        case .react: return String(localized: "React")
        case .translate: return String(localized: "Translate")
        case .bookmark: return String(localized: "Bookmark")
        case .favorite: return String(localized: "Favorite")
        case .share: return String(localized: "Share")
        case .fxShare: return String(localized: "Fx share")
        case .delete: return String(localized: "Delete")
        case .report: return String(localized: "Report")
        case .muteUser: return String(localized: "Mute user")
        case .blockUser: return String(localized: "Block user")
        }
    }

    var fontAwesomeIcon: FontAwesomeIcon {
        switch self {
        case .reply: return .reply
        case .comment: return .commentDots
        case .repost: return .retweet
        case .quote: return .reply
        case .like: return .heart
        case .react: return .plus
        case .translate: return .language
        case .bookmark: return .bookmark
        case .favorite: return .star
        case .share: return .shareNodes
        case .fxShare: return .shareNodes
        case .delete: return .trash
        case .report: return .circleInfo
        case .muteUser: return .volumeXmark
        case .blockUser: return .userSlash
        }
    }
}

private extension TimelineAppearance {
    func withPostActionLayout(_ config: PostActionLayoutConfig) -> TimelineAppearance {
        doCopy(
            avatarShape: avatarShape,
            showMedia: showMedia,
            showSensitiveContent: showSensitiveContent,
            expandContentWarning: expandContentWarning,
            expandMediaSize: expandMediaSize,
            videoAutoplay: videoAutoplay,
            showLinkPreview: showLinkPreview,
            compatLinkPreview: compatLinkPreview,
            showNumbers: showNumbers,
            postActionStyle: postActionStyle,
            postActionLayout: config,
            fullWidthPost: fullWidthPost,
            absoluteTimestamp: absoluteTimestamp,
            showPlatformLogo: showPlatformLogo,
            timelineDisplayMode: timelineDisplayMode,
            aiConfig: aiConfig,
            lineLimit: lineLimit,
            showTranslateButton: showTranslateButton
        )
    }
}

private func castPostActionFamilies(_ value: Any) -> [PostActionFamily] {
    if let families = value as? [PostActionFamily] {
        return families
    }
    if let families = value as? NSArray {
        return families.cast(PostActionFamily.self)
    }
    return []
}
