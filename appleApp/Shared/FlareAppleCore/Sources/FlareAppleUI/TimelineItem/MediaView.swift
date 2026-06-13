import SwiftUI
import KotlinSharedUI
import AppleFontAwesome

struct MediaView: View {
    let data: UiMedia
    
    init(data: UiMedia) {
        self.data = data
    }

    var body: some View {
        ZStack {
            switch onEnum(of: data) {
            case .image(let image):
                Color.gray
                    .overlay {
                        NetworkImage(data: image.previewUrl, customHeader: image.customHeaders)
                            .allowsHitTesting(false)
                    }
                    .clipped()
            case .video(let video):
                Color.gray
                    .overlay {
                        NetworkImage(data: video.thumbnailUrl, customHeader: video.customHeaders)
                            .allowsHitTesting(false)
                    }
                    .overlay(alignment: .bottomLeading) {
                        Image(fontAwesome: .circlePlay)
                            .foregroundStyle(.white)
                            .padding(8)
                            .background(.black, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .padding()
                    }
                    .clipped()
            case .gif(let gif):
                Color.gray
                    .overlay {
                        NetworkImage(data: gif.url, customHeader: gif.customHeaders)
                            .allowsHitTesting(false)
                    }
                    .clipped()
            case .audio:
                Color.gray
                    .overlay {
                        Image(systemName: "waveform")
                            .foregroundStyle(.white)
                    }
            }
        }
    }
}
