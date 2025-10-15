import SwiftUI
import KotlinSharedUI
import LazyPager
import AVKit
import Photos
import Kingfisher

struct StatusMediaScreen: View {
    @Environment(\.dismiss) var dismiss
    let data: [UiMedia]
    let initialIndex: Int
    @State private var selectedIndex: Int = 0
    @State var opacity: CGFloat = 1 // Dismiss gesture background opacity
    @State var shouldShow = false
    var body: some View {
        if shouldShow {
            LazyPager(data: data, page: $selectedIndex) { media in
                switch onEnum(of: media) {
                case .image(let image):
                    NetworkImage(data: image.url, placeholder: image.previewUrl)
                        .scaledToFit()
                case .video(let video):
                    if selectedIndex == data.firstIndex(where: { $0.url == video.url }) {
                        let player = AVPlayer(url: .init(string: video.url)!)

                        VideoPlayer(player: player)
                            .onAppear {
                                player.play()
                            }
                            .onChange(of: selectedIndex) { oldValue, newValue in
                                if newValue != data.firstIndex(where: { $0.url == video.url }) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        NetworkImage(data: video.thumbnailUrl)
                            .scaledToFit()
                    }
                case .gif(let gif):
                    NetworkImage(data: gif.url, placeholder: gif.previewUrl)
                        .scaledToFit()
                case .audio(let audio):
                    EmptyView()
                }
            }
            .onDismiss(backgroundOpacity: $opacity) {
                dismiss()
            }
            .zoomable { item in
                if case .video = onEnum(of: item) {
                    return .disabled
                } else {
                    return .custom(min: 1, max: 5, doubleTap: .scale(2))
                }
            }
            .settings { config in
                config.preloadAmount = 99
            }
            .background(.black.opacity(opacity))
            .background(ClearFullScreenBackground())
            .ignoresSafeArea()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                    } label: {
                        Image("fa-xmark")
                    }
                }
                let selectedMedia = data[selectedIndex]
                if case .image = onEnum(of: selectedMedia) {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            MediaSaver.shared.saveImage(url: selectedMedia.url)
                        } label: {
                            Image("fa-download")
                        }
                    }
                }
            }
        } else {
            Color.black
                .onAppear {
                    selectedIndex = initialIndex
                    shouldShow = true
                }
        }
    }
    
}

extension StatusMediaScreen {
    init(data: [UiMedia], selectedIndex: Int) {
        self.data = data
        self.initialIndex = selectedIndex
    }
}
