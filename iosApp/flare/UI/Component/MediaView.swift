import SwiftUI
import KotlinSharedUI

struct MediaView: View {
    let data: UiMedia
    var body: some View {
        switch onEnum(of: data) {
        case .image(let image):
            NetworkImage(data: image.previewUrl)
                .clipped()
        case .video(let video):
            NetworkImage(data: video.thumbnailUrl)
                .clipped()
        case .gif(let gif):
            NetworkImage(data: gif.previewUrl)
                .clipped()
        case .audio(let audio):
            EmptyView()
        }
    }
}
