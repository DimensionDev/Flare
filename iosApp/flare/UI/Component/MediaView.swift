import SwiftUI
import TipKit
import KotlinSharedUI

struct MediaView: View {
    let data: UiMedia
    var body: some View {
        switch onEnum(of: data) {
        case .image(let image):
            NetworkImage(data: image.previewUrl)
        case .video(let video):
            NetworkImage(data: video.thumbnailUrl)
        case .gif(let gif):
            NetworkImage(data: gif.url)
        case .audio(let audio):
            EmptyView()
        }
    }
}
