import SwiftUI
import shared
import Kingfisher
import AVKit

struct FullScreenImageViewer: View {
    let media: UiMedia
    public var body: some View {
        ZStack {
            switch onEnum(of: media) {
            case .image(let data):
                KFImage(URL(string: data.url))
                    .resizable()
                    .aspectRatio(contentMode: .fit)
            case .video(let video):
                VideoPlayer(player: AVPlayer(url: URL(string: video.url)!))
            case .audio(let audio):
                VideoPlayer(player: AVPlayer(url: URL(string: audio.url)!))
            case .gif(let gif):
                VideoPlayer(player: AVPlayer(url: URL(string: gif.url)!))
            }
        }
    }
}
