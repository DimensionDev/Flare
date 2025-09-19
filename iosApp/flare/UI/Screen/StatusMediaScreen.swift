import SwiftUI
import KotlinSharedUI
import LazyPager
import AVKit

struct StatusMediaScreen: View {
    @Environment(\.dismiss) var dismiss
    let data: [UiMedia]
    @State private var selectedIndex: Int = 0
    @State var opacity: CGFloat = 1 // Dismiss gesture background opacity
    var body: some View {
        LazyPager(data: data, page: $selectedIndex) { media in
            switch onEnum(of: media) {
            case .image(let image):
                NetworkImage(data: image.url, placeholder: image.previewUrl)
                    .aspectRatio(contentMode: .fit)
            case .video(let video):
                if selectedIndex == data.firstIndex(where: { $0.url == video.url }) {
                    let player = AVPlayer(url: .init(string: video.url)!)
                    VideoPlayer(player: player)
                        .onAppear {
                            player.play()
                        }
                        .onChange(of: selectedIndex) { newValue in
                            if newValue != data.firstIndex(where: { $0.url == video.url }) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .aspectRatio(contentMode: .fit)
                } else {
                    NetworkImage(data: video.thumbnailUrl)
                        .aspectRatio(contentMode: .fit)
                }
            case .gif(let gif):
                NetworkImage(data: gif.url, placeholder: gif.previewUrl)
                    .aspectRatio(contentMode: .fit)
            case .audio(let audio):
                EmptyView()
            }
        }
        .onDismiss(backgroundOpacity: $opacity) {
            dismiss()
        }
        .zoomable(min: 1, max: 5)
        .background(.black.opacity(opacity))
        .background(ClearFullScreenBackground())
        .ignoresSafeArea()
        .overlay(alignment: .topLeading) {
            Button {
                dismiss()
            } label: {
                Image("fa-xmark")
            }
            .buttonStyle(.glass)
            .padding()
        }
        .colorScheme(.dark)
    }
}

extension StatusMediaScreen {
    init(data: [UiMedia], selectedIndex: Int) {
        self.data = data
        self.selectedIndex = selectedIndex
    }
}
