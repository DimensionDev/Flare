import SwiftUI
import TipKit
import KotlinSharedUI

struct MediaView: View {
    let data: UiMedia
    @State private var showAlt: Bool = false
    var body: some View {
        switch onEnum(of: data) {
        case .image(let image):
            NetworkImage(data: image.previewUrl)
                .overlay(alignment: .bottomTrailing) {
                    if let alt = image.description_ {
                        Button {
                            showAlt.toggle()
                        } label: {
                            Text("ALT")
                        }
                        .popover(isPresented: $showAlt) {
                            Text(alt)
                                .padding()
                        }
                        .padding()
                        .buttonStyle(.glass)
                    }
                }
        case .video(let video):
            NetworkImage(data: video.thumbnailUrl)
                .overlay(alignment: .bottomTrailing) {
                    if let alt = video.description_ {
                        Button {
                            showAlt.toggle()
                        } label: {
                            Text("ALT")
                        }
                        .popover(isPresented: $showAlt) {
                            Text(alt)
                                .padding()
                        }
                        .padding()
                        .buttonStyle(.glass)
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
            NetworkImage(data: gif.url)
                .overlay(
                    alignment: .bottomTrailing
                ) {
                    if let alt = gif.description_ {
                        Button {
                            showAlt.toggle()
                        } label: {
                            Text("ALT")
                        }
                        .popover(isPresented: $showAlt) {
                            Text(alt)
                                .padding()
                        }
                        .buttonStyle(.glass)
                        .padding()
                    }
                }
        case .audio(let audio):
            EmptyView()
        }
    }
}
