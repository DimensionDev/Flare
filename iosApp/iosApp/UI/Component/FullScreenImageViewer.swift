import SwiftUI
import shared
import NetworkImage
import AVKit

struct FullScreenImageViewer: View {
    let media: UiMedia
    public var body: some View {
        ZStack {
            switch onEnum(of: media) {
            case .image(let data):
                NetworkImage(url: .init(string: data.url)) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                }
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
