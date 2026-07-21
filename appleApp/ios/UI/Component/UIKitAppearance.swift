import KotlinSharedUI
import UIKit
import FlareAppleUI

private func contentSizeCategory(fontSizeDiff: Float) -> UIContentSizeCategory {
    switch Int(fontSizeDiff.rounded()) {
    case ...(-2):
        return .extraSmall
    case -1:
        return .small
    case 0:
        return .medium
    case 1:
        return .large
    case 2:
        return .extraLarge
    case 3:
        return .extraExtraLarge
    default:
        return .extraExtraExtraLarge
    }
}

struct StatusUIKitAppearance: Equatable {
    let preferredContentSizeCategory: UIContentSizeCategory
    let preferredContentSizeCategoryID: String
    let fullWidthPost: Bool
    let avatarShape: AvatarShape
    let avatarShapeID: String
    let showPlatformLogo: Bool
    let absoluteTimestamp: Bool
    let postActionStyle: PostActionStyle
    let postActionStyleID: String
    let postActionLayout: PostActionLayoutConfig
    let postActionLayoutID: String
    let postActionFixedWidth: Bool
    let showNumbers: Bool
    let showMedia: Bool
    let showSensitiveContent: Bool
    let expandContentWarning: Bool
    let showLinkPreview: Bool
    let compatLinkPreview: Bool
    let expandMediaSize: Bool
    let limitMediaGridToNine: Bool
    let aiAgentEnabled: Bool
    let showOriginalWithTranslation: Bool

    init(
        timeline: TimelineAppearance,
        fontSizeDiff: Float = 0,
        showOriginalWithTranslation: Bool = false
    ) {
        preferredContentSizeCategory = contentSizeCategory(fontSizeDiff: fontSizeDiff)
        preferredContentSizeCategoryID = preferredContentSizeCategory.rawValue
        fullWidthPost = timeline.fullWidthPost
        avatarShape = timeline.avatarShape
        avatarShapeID = timeline.avatarShape.name
        showPlatformLogo = timeline.showPlatformLogo
        absoluteTimestamp = timeline.absoluteTimestamp
        postActionStyle = timeline.postActionStyle
        postActionStyleID = timeline.postActionStyle.name
        postActionLayout = timeline.postActionLayout
        postActionLayoutID = PostActionLayoutHelpers.shared.signature(config: timeline.postActionLayout)
        postActionFixedWidth = timeline.postActionFixedWidth
        showNumbers = timeline.showNumbers
        showMedia = timeline.showMedia
        showSensitiveContent = timeline.showSensitiveContent
        expandContentWarning = timeline.expandContentWarning
        showLinkPreview = timeline.showLinkPreview
        compatLinkPreview = timeline.compatLinkPreview
        expandMediaSize = timeline.expandMediaSize
        limitMediaGridToNine = timeline.limitMediaGridToNine
        aiAgentEnabled = timeline.aiConfig.agent
        self.showOriginalWithTranslation = showOriginalWithTranslation
    }

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.preferredContentSizeCategoryID == rhs.preferredContentSizeCategoryID &&
            lhs.fullWidthPost == rhs.fullWidthPost &&
            lhs.avatarShapeID == rhs.avatarShapeID &&
            lhs.showPlatformLogo == rhs.showPlatformLogo &&
            lhs.absoluteTimestamp == rhs.absoluteTimestamp &&
            lhs.postActionStyleID == rhs.postActionStyleID &&
            lhs.postActionLayoutID == rhs.postActionLayoutID &&
            lhs.postActionFixedWidth == rhs.postActionFixedWidth &&
            lhs.showNumbers == rhs.showNumbers &&
            lhs.showMedia == rhs.showMedia &&
            lhs.showSensitiveContent == rhs.showSensitiveContent &&
            lhs.expandContentWarning == rhs.expandContentWarning &&
            lhs.showLinkPreview == rhs.showLinkPreview &&
            lhs.compatLinkPreview == rhs.compatLinkPreview &&
            lhs.expandMediaSize == rhs.expandMediaSize &&
            lhs.limitMediaGridToNine == rhs.limitMediaGridToNine &&
            lhs.aiAgentEnabled == rhs.aiAgentEnabled &&
            lhs.showOriginalWithTranslation == rhs.showOriginalWithTranslation
    }
}

struct TimelineUIKitAppearance: Equatable {
    let status: StatusUIKitAppearance
    let timelineDisplayMode: TimelineDisplayMode
    let timelineDisplayModeID: String
    let videoAutoplay: VideoAutoplay
    let videoAutoplayID: String

    var isPlainTimelineDisplayMode: Bool {
        timelineDisplayMode == .plain
    }

    var usesCardBackground: Bool {
        timelineDisplayMode == .card
    }

    init(
        timeline: TimelineAppearance,
        fontSizeDiff: Float = 0,
        showOriginalWithTranslation: Bool = false
    ) {
        status = StatusUIKitAppearance(
            timeline: timeline,
            fontSizeDiff: fontSizeDiff,
            showOriginalWithTranslation: showOriginalWithTranslation
        )
        timelineDisplayMode = timeline.timelineDisplayMode
        timelineDisplayModeID = timeline.timelineDisplayMode.name
        videoAutoplay = timeline.videoAutoplay
        videoAutoplayID = timeline.videoAutoplay.name
    }

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.status == rhs.status &&
            lhs.timelineDisplayModeID == rhs.timelineDisplayModeID &&
            lhs.videoAutoplayID == rhs.videoAutoplayID
    }
}

struct GalleryUIKitAppearance: Equatable {
    let showMedia: Bool
    let avatarShape: AvatarShape
    let avatarShapeID: String
    let showOriginalWithTranslation: Bool

    init(timeline: TimelineAppearance, showOriginalWithTranslation: Bool = false) {
        showMedia = timeline.showMedia
        avatarShape = timeline.avatarShape
        avatarShapeID = timeline.avatarShape.name
        self.showOriginalWithTranslation = showOriginalWithTranslation
    }

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.showMedia == rhs.showMedia &&
            lhs.avatarShapeID == rhs.avatarShapeID &&
            lhs.showOriginalWithTranslation == rhs.showOriginalWithTranslation
    }
}
