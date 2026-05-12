import KotlinSharedUI

struct StatusUIKitAppearance: Equatable {
    let fullWidthPost: Bool
    let avatarShape: AvatarShape
    let avatarShapeID: String
    let showPlatformLogo: Bool
    let absoluteTimestamp: Bool
    let postActionStyle: PostActionStyle
    let postActionStyleID: String
    let showNumbers: Bool
    let showMedia: Bool
    let showSensitiveContent: Bool
    let showLinkPreview: Bool
    let compatLinkPreview: Bool
    let expandMediaSize: Bool

    init(timeline: TimelineAppearance) {
        fullWidthPost = timeline.fullWidthPost
        avatarShape = timeline.avatarShape
        avatarShapeID = timeline.avatarShape.name
        showPlatformLogo = timeline.showPlatformLogo
        absoluteTimestamp = timeline.absoluteTimestamp
        postActionStyle = timeline.postActionStyle
        postActionStyleID = timeline.postActionStyle.name
        showNumbers = timeline.showNumbers
        showMedia = timeline.showMedia
        showSensitiveContent = timeline.showSensitiveContent
        showLinkPreview = timeline.showLinkPreview
        compatLinkPreview = timeline.compatLinkPreview
        expandMediaSize = timeline.expandMediaSize
    }

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.fullWidthPost == rhs.fullWidthPost &&
            lhs.avatarShapeID == rhs.avatarShapeID &&
            lhs.showPlatformLogo == rhs.showPlatformLogo &&
            lhs.absoluteTimestamp == rhs.absoluteTimestamp &&
            lhs.postActionStyleID == rhs.postActionStyleID &&
            lhs.showNumbers == rhs.showNumbers &&
            lhs.showMedia == rhs.showMedia &&
            lhs.showSensitiveContent == rhs.showSensitiveContent &&
            lhs.showLinkPreview == rhs.showLinkPreview &&
            lhs.compatLinkPreview == rhs.compatLinkPreview &&
            lhs.expandMediaSize == rhs.expandMediaSize
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

    init(timeline: TimelineAppearance) {
        status = StatusUIKitAppearance(timeline: timeline)
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

    init(timeline: TimelineAppearance) {
        showMedia = timeline.showMedia
        avatarShape = timeline.avatarShape
        avatarShapeID = timeline.avatarShape.name
    }

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.showMedia == rhs.showMedia &&
            lhs.avatarShapeID == rhs.avatarShapeID
    }
}
