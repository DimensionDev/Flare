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

    init(settings: AppearanceSettings) {
        fullWidthPost = settings.fullWidthPost
        avatarShape = settings.avatarShape
        avatarShapeID = settings.avatarShape.name
        showPlatformLogo = settings.showPlatformLogo
        absoluteTimestamp = settings.absoluteTimestamp
        postActionStyle = settings.postActionStyle
        postActionStyleID = settings.postActionStyle.name
        showNumbers = settings.showNumbers
        showMedia = settings.showMedia
        showSensitiveContent = settings.showSensitiveContent
        showLinkPreview = settings.showLinkPreview
        compatLinkPreview = settings.compatLinkPreview
        expandMediaSize = settings.expandMediaSize
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
        !isPlainTimelineDisplayMode
    }

    init(settings: AppearanceSettings) {
        status = StatusUIKitAppearance(settings: settings)
        timelineDisplayMode = settings.timelineDisplayMode
        timelineDisplayModeID = settings.timelineDisplayMode.name
        videoAutoplay = settings.videoAutoplay
        videoAutoplayID = settings.videoAutoplay.name
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

    init(settings: AppearanceSettings) {
        showMedia = settings.showMedia
        avatarShape = settings.avatarShape
        avatarShapeID = settings.avatarShape.name
    }

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.showMedia == rhs.showMedia &&
            lhs.avatarShapeID == rhs.avatarShapeID
    }
}
