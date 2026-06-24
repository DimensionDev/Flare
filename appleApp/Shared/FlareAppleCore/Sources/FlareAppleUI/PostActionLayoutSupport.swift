import FlareAppleCore
import KotlinSharedUI
import SwiftUI

public enum PostActionLayoutSupport {
    public static let placements: [PostActionPlacement] = [.buttonRow, .moreMenu, .hidden]

    public static func signature(config: PostActionLayoutConfig) -> String {
        PostActionLayoutHelpers.shared.signature(config: config)
    }

    public static func normalizedForEdit(config: PostActionLayoutConfig) -> PostActionLayoutConfig {
        PostActionLayoutHelpers.shared.normalizedForEdit(config: config)
    }

    public static func withEnabled(
        config: PostActionLayoutConfig,
        enabled: Bool
    ) -> PostActionLayoutConfig {
        PostActionLayoutHelpers.shared.withEnabled(config: config, enabled: enabled)
    }

    public static func families(
        config: PostActionLayoutConfig,
        placement: PostActionPlacement
    ) -> [PostActionFamily] {
        castPostActionFamilies(
            PostActionLayoutHelpers.shared.familiesFor(
                config: config,
                placement: placement
            )
        )
    }

    public static func moveTo(
        config: PostActionLayoutConfig,
        family: PostActionFamily,
        placement: PostActionPlacement
    ) -> PostActionLayoutConfig {
        PostActionLayoutHelpers.shared.moveTo(
            config: config,
            family: family,
            placement: placement
        )
    }

    public static func moveBy(
        config: PostActionLayoutConfig,
        family: PostActionFamily,
        offset: Int32
    ) -> PostActionLayoutConfig {
        PostActionLayoutHelpers.shared.moveBy(
            config: config,
            family: family,
            offset: offset
        )
    }

    public static func moveAt(
        config: PostActionLayoutConfig,
        placement: PostActionPlacement,
        fromIndex: Int32,
        toOffset: Int32
    ) -> PostActionLayoutConfig {
        PostActionLayoutHelpers.shared.moveAt(
            config: config,
            placement: placement,
            fromIndex: fromIndex,
            toOffset: toOffset
        )
    }
}

public struct PostActionLayoutPreview: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AppearancePresenter())
    @Environment(\.timelineAppearance) private var timelineAppearance
    private let config: PostActionLayoutConfig

    public init(config: PostActionLayoutConfig) {
        self.config = config
    }

    public var body: some View {
        StateView(state: presenter.state.sampleStatus) { status in
            TimelineView(data: PostActionLayoutPreviewHelper.shared.withPreviewActions(post: status))
                .environment(
                    \.timelineAppearance,
                    timelineAppearance.withPostActionLayoutOverride(config)
                )
        }
    }
}

public extension PostActionPlacement {
    var postActionLayoutTitleKey: LocalizedStringKey {
        switch self {
        case .buttonRow:
            "post_action_layout_button_row"
        case .moreMenu:
            "post_action_layout_more_menu"
        case .hidden:
            "post_action_layout_hidden"
        }
    }

    var postActionLayoutMoveTitleKey: LocalizedStringKey {
        switch self {
        case .buttonRow:
            "post_action_layout_move_to_button_row"
        case .moreMenu:
            "post_action_layout_move_to_more_menu"
        case .hidden:
            "post_action_layout_hide_action"
        }
    }
}

public extension PostActionFamily {
    var postActionLayoutTitleKey: LocalizedStringKey {
        switch self {
        case .reply:
            "post_action_family_reply"
        case .comment:
            "post_action_family_comment"
        case .repost:
            "post_action_family_repost"
        case .quote:
            "post_action_family_quote"
        case .like:
            "post_action_family_like"
        case .react:
            "post_action_family_react"
        case .translate:
            "post_action_family_translate"
        case .bookmark:
            "post_action_family_bookmark"
        case .favorite:
            "post_action_family_favorite"
        case .share:
            "post_action_family_share"
        case .fxShare:
            "post_action_family_fx_share"
        case .delete:
            "post_action_family_delete"
        case .report:
            "post_action_family_report"
        case .muteUser:
            "post_action_family_mute_user"
        case .blockUser:
            "post_action_family_block_user"
        }
    }

    var postActionLayoutIcon: FontAwesomeIcon {
        switch self {
        case .reply:
            .reply
        case .comment:
            .commentDots
        case .repost:
            .retweet
        case .quote:
            .reply
        case .like:
            .heart
        case .react:
            .plus
        case .translate:
            .language
        case .bookmark:
            .bookmark
        case .favorite:
            .star
        case .share:
            .shareNodes
        case .fxShare:
            .shareNodes
        case .delete:
            .trash
        case .report:
            .circleInfo
        case .muteUser:
            .volumeXmark
        case .blockUser:
            .userSlash
        }
    }
}

public extension TimelineAppearance {
    func withPostActionLayoutOverride(_ config: PostActionLayoutConfig) -> TimelineAppearance {
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
