import SwiftUI
import shared
import AVKit

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
        self.id = media.url
        self.url = URL(string: media.url) ?? URL(string: "about:blank")!
        self.previewUrl = URL(string: media.previewUrl)
        self.mediaKind = .image
        self.videoMedia = nil
        self.playableAsset = nil
        self.width = media.width
        self.height = media.height
        self.description = media.description_
        self.isActive = true
    }
    
    public init(media: UiMediaVideo) {
        self.id = media.url
        self.url = URL(string: media.url) ?? URL(string: "about:blank")!
        self.previewUrl = URL(string: media.thumbnailUrl)
        self.mediaKind = .video
        self.videoMedia = VideoMedia(state: .loading)
        self.playableAsset = nil
        self.width = media.width
        self.height = media.height
        self.description = media.description_
        self.isActive = true
    }
    
    public init(media: UiMediaAudio) {
        self.id = media.url
        self.url = URL(string: media.url) ?? URL(string: "about:blank")!
        self.previewUrl = media.previewUrl.flatMap { URL(string: $0) }
        self.mediaKind = .audio
        self.videoMedia = nil
        self.playableAsset = nil
        self.width = 0
        self.height = 0
        self.description = media.description_
        self.isActive = true
    }
    
    public init(media: UiMediaGif) {
        self.id = media.url
        self.url = URL(string: media.url) ?? URL(string: "about:blank")!
        self.previewUrl = URL(string: media.previewUrl)
        self.mediaKind = .gif
        self.videoMedia = nil
        self.playableAsset = nil
        self.width = media.width
        self.height = media.height
        self.description = media.description_
        self.isActive = true
    }
    
    public init(media: UiMedia) {
        switch onEnum(of: media) {
        case .image(let image):
            self.init(media: image)
        case .video(let video):
            self.init(media: video)
        case .audio(let audio):
            self.init(media: audio)
        case .gif(let gif):
            self.init(media: gif)
        }
    }
}

extension Array where Element == UiMedia {
    func asMediaViewModels() -> [FeedMediaViewModel] {
        self.map { media -> FeedMediaViewModel in
            switch onEnum(of: media) {
            case .image(let image):
                return FeedMediaViewModel(media: image)
            case .video(let video):
                return FeedMediaViewModel(media: video)
            case .audio(let audio):
                return FeedMediaViewModel(media: audio)
            case .gif(let gif):
                return FeedMediaViewModel(media: gif)
            }
        }
    }
}
