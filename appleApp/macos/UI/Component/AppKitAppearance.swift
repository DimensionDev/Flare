import AppKit
import KotlinSharedUI

struct StatusAppKitAppearance: Equatable {
    let fontSizeDiff: CGFloat
    let bodyFontSize: CGFloat
    let captionFontSize: CGFloat
    let fullWidthPost: Bool
    let avatarShapeID: String
    let showPlatformLogo: Bool
    let absoluteTimestamp: Bool
    let postActionStyle: PostActionStyle
    let postActionStyleID: String
    let showNumbers: Bool
    let showMedia: Bool
    let showSensitiveContent: Bool
    let expandContentWarning: Bool
    let showLinkPreview: Bool
    let compatLinkPreview: Bool
    let expandMediaSize: Bool
    let aiAgentEnabled: Bool

    init(timeline: TimelineAppearance, fontSizeDiff: Float = 0) {
        let diff = CGFloat(fontSizeDiff)
        self.fontSizeDiff = diff
        bodyFontSize = max(11, 14 + diff)
        captionFontSize = max(9, 12 + diff)
        fullWidthPost = timeline.fullWidthPost
        avatarShapeID = timeline.avatarShape.name
        showPlatformLogo = timeline.showPlatformLogo
        absoluteTimestamp = timeline.absoluteTimestamp
        postActionStyle = timeline.postActionStyle
        postActionStyleID = timeline.postActionStyle.name
        showNumbers = timeline.showNumbers
        showMedia = timeline.showMedia
        showSensitiveContent = timeline.showSensitiveContent
        expandContentWarning = timeline.expandContentWarning
        showLinkPreview = timeline.showLinkPreview
        compatLinkPreview = timeline.compatLinkPreview
        expandMediaSize = timeline.expandMediaSize
        aiAgentEnabled = timeline.aiConfig.agent
    }

    var bodyFont: NSFont {
        .systemFont(ofSize: bodyFontSize)
    }

    var bodyBoldFont: NSFont {
        .boldSystemFont(ofSize: bodyFontSize)
    }

    var captionFont: NSFont {
        .systemFont(ofSize: captionFontSize)
    }
}

struct TimelineAppKitAppearance: Equatable {
    let status: StatusAppKitAppearance
    let timelineDisplayMode: TimelineDisplayMode
    let timelineDisplayModeID: String
    let videoAutoplayID: String

    var isPlainTimelineDisplayMode: Bool {
        timelineDisplayMode == .plain
    }

    var usesCardBackground: Bool {
        timelineDisplayMode == .card
    }

    init(timeline: TimelineAppearance, fontSizeDiff: Float = 0) {
        status = StatusAppKitAppearance(timeline: timeline, fontSizeDiff: fontSizeDiff)
        timelineDisplayMode = timeline.timelineDisplayMode
        timelineDisplayModeID = timeline.timelineDisplayMode.name
        videoAutoplayID = timeline.videoAutoplay.name
    }
}
