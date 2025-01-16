import AVKit
import shared
import SwiftUI

public enum MediaKind {
    case image
    case video
    case audio
    case gif
}

public struct VideoMedia {
    enum State {
        case loading
        case finish(MediaInfo?)
    }

    let state: State
}

public struct MediaInfo {
    let duration: String?
}

public struct PlayableAsset {
    let duration: String?
}

public struct FeedMediaViewModel: Identifiable {
    public let id: String
    public let url: URL
    public let previewUrl: URL?
    public let mediaKind: MediaKind
    public let videoMedia: VideoMedia?
    public let playableAsset: PlayableAsset?
    public let width: Float
    public let height: Float
    public let description: String?
    public var isActive: Bool = true

    public init(media: UiMediaImage) {
        id = media.url
        url = URL(string: media.url) ?? URL(string: "about:blank")!
        previewUrl = URL(string: media.previewUrl)
        mediaKind = .image
        videoMedia = nil
        playableAsset = nil
        width = media.width
        height = media.height
        description = media.description_
        isActive = true
    }

    public init(media: UiMediaVideo) {
        id = media.url
        url = URL(string: media.url) ?? URL(string: "about:blank")!
        previewUrl = URL(string: media.thumbnailUrl)
        mediaKind = .video
        videoMedia = VideoMedia(state: .loading)
        playableAsset = nil
        width = media.width
        height = media.height
        description = media.description_
        isActive = true
    }

    public init(media: UiMediaAudio) {
        id = media.url
        url = URL(string: media.url) ?? URL(string: "about:blank")!
        previewUrl = media.previewUrl.flatMap { URL(string: $0) }
        mediaKind = .audio
        videoMedia = nil
        playableAsset = nil
        width = 0
        height = 0
        description = media.description_
        isActive = true
    }

    public init(media: UiMediaGif) {
        id = media.url
        url = URL(string: media.url) ?? URL(string: "about:blank")!
        previewUrl = URL(string: media.previewUrl)
        mediaKind = .gif
        videoMedia = nil
        playableAsset = nil
        width = media.width
        height = media.height
        description = media.description_
        isActive = true
    }

    public init(media: UiMedia) {
        switch onEnum(of: media) {
        case let .image(image):
            self.init(media: image)
        case let .video(video):
            self.init(media: video)
        case let .audio(audio):
            self.init(media: audio)
        case let .gif(gif):
            self.init(media: gif)
        }
    }
}

extension [UiMedia] {
    func asMediaViewModels() -> [FeedMediaViewModel] {
        map { media -> FeedMediaViewModel in
            switch onEnum(of: media) {
            case let .image(image):
                return FeedMediaViewModel(media: image)
            case let .video(video):
                return FeedMediaViewModel(media: video)
            case let .audio(audio):
                return FeedMediaViewModel(media: audio)
            case let .gif(gif):
                return FeedMediaViewModel(media: gif)
            }
        }
    }
}
