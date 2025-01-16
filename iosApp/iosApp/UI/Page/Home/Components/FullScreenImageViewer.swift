import AVKit
import Kingfisher
import shared
import SwiftUI

struct FullScreenImageViewer: View {
    let media: UiMedia
    public var body: some View {
        ZStack {
            switch onEnum(of: media) {
            case let .image(data):
                KFImage(URL(string: data.url))
                    .resizable()
                    .aspectRatio(contentMode: .fit)
            case let .video(video):
                VideoPlayer(player: AVPlayer(url: URL(string: video.url)!))
            case let .audio(audio):
                VideoPlayer(player: AVPlayer(url: URL(string: audio.url)!))
            case let .gif(gif):
                VideoPlayer(player: AVPlayer(url: URL(string: gif.url)!))
            }
        }
    }
}
