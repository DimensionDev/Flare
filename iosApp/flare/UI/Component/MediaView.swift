import SwiftUI
import KotlinSharedUI

struct MediaView: View {
    let data: UiMedia
    var body: some View {
        switch onEnum(of: data) {
        case .image(let image):
            NetworkImage(data: image.previewUrl)
                .overlay(alignment: .bottomTrailing) {
                    if let alt = image.description_ {
                        Text("ALT")
                            .padding(8)
                            .background(.black, in: .rect(cornerRadius: 16))
                            .foregroundStyle(.white)
                            .padding()
                            .frame(alignment: .bottomTrailing)
                    }
                }
        case .video(let video):
            NetworkImage(data: video.thumbnailUrl)
                .overlay(alignment: .bottomTrailing) {
                    if let alt = video.description_ {
                        Text("ALT")
                            .padding(8)
                            .background(.black, in: .rect(cornerRadius: 16))
                            .foregroundStyle(.white)
                            .padding()
                            .frame(alignment: .bottomTrailing)
                    }
                }
                .overlay(alignment: .bottomLeading) {
                    Image("fa-circle-play")
                        .foregroundStyle(Color(.white))
                        .padding(8)
                        .background(.black, in: .rect(cornerRadius: 16))
                        .padding()
                        .frame(alignment: .bottomLeading)
                }
        case .gif(let gif):
            NetworkImage(data: gif.previewUrl)
                .overlay(
                    alignment: .bottomTrailing
                ) {
                    if let alt = gif.description_ {
                        Text("ALT")
                            .padding(8)
                            .background(.black, in: .rect(cornerRadius: 16))
                            .foregroundStyle(.white)
                            .padding()
                            .frame(alignment: .bottomTrailing)
                    }
                }
        case .audio(let audio):
            EmptyView()
        }
    }
}
