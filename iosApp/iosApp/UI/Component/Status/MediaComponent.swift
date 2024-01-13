import SwiftUI
import shared
import NetworkImage
import AVKit

struct MediaComponent: View {
    @State var hideSensitive: Bool
    let medias: [UiMedia]
    let onMediaClick: (UiMedia) -> Void
    var body: some View {
        let showSensitiveButton = medias.allSatisfy { media in
            media is UiMediaImage
        }
        let columns = if medias.count == 1 {
            [GridItem(.flexible())]
        } else if medias.count < 5 {
            [GridItem(.flexible()), GridItem(.flexible())]
        } else {
            [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())]
        }
        ZStack(alignment: .topLeading) {
            LazyVGrid(columns: columns) {
                if medias.count == 1 {
                    switch onEnum(of: medias[0]) {
                    case .image(let image):
                        MediaItemComponent(media: medias[0])
                            .aspectRatio(.init(image.aspectRatio), contentMode: .fill)
                    case .video(let video):
                        MediaItemComponent(media: medias[0])
                            .aspectRatio(.init(video.aspectRatio), contentMode: .fill)
                    case .gif(let gif):
                        MediaItemComponent(media: medias[0])
                            .aspectRatio(.init(gif.aspectRatio), contentMode: .fill)
                    case .audio:
                        MediaItemComponent(media: medias[0])
                            .frame(minHeight: 48)
                    }
                } else {
                    ForEach(1...medias.count, id: \.self) { index in
                        MediaItemComponent(media: medias[index - 1])
                            .aspectRatio(1, contentMode: .fill)
                    }
                }
            }
            .if(hideSensitive, transform: { view in
                view.blur(radius: 32)
            })
            if showSensitiveButton {
                if hideSensitive {
                    Button(action: {
                        withAnimation {
                            hideSensitive = false
                        }
                    }, label: {
                        Color.clear
                    })
                    .buttonStyle(.borderless)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    Button(action: {
                        withAnimation {
                            hideSensitive = false
                        }
                    }, label: {
                        Text("Show Media")
                    })
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                } else {
                    Button(action: {
                        withAnimation {
                            hideSensitive = true
                        }
                    }, label: {
                        Image(systemName: "eye.slash")
                    })
                    .padding()
                    .buttonStyle(.borderedProminent)
                    .tint(Color.primary)
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                }
            }
        }
        .frame(maxWidth: 600)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

struct MediaItemComponent: View {
#if !os(macOS)
    @State var showCover = false
#else
    @Environment(\.openWindow) var openWindow
#endif
    let media: UiMedia
    var body: some View {
        ZStack {
            Color.clear.overlay {
                switch onEnum(of: media) {
                case .image(let data):
                    NetworkImage(url: URL(string: data.previewUrl)) { image in
                        image.resizable().scaledToFill()
                    }
                case .video(let video):
                    MutedVideoPlayer(url: video.url)
                case .audio(let audio):
                    VideoPlayer(player: AVPlayer(url: URL(string: audio.url)!)) {
                        if let cover = audio.previewUrl {
                            NetworkImage(url: URL(string: cover)) { image in
                                image.resizable().scaledToFill()
                            }
                        }
                    }
                case .gif(let gif):
                    MutedVideoPlayer(url: gif.url)
                }
            }
        }
        .clipped()
#if os(macOS)
        .onTapGesture {
            switch onEnum(of: media) {
            case .image(let data):
                openWindow(id: "image-view", value: data.url)
            case .video(let video):
                openWindow(id: "video-view", value: video.url)
            case .audio(let audio):
                openWindow(id: "video-view", value: audio.url)
            case .gif(let gif):
                openWindow(id: "video-view", value: gif.url)
            }
        }
#else
        .onTapGesture {
            showCover = true
        }
        .fullScreenCover(
            isPresented: $showCover,
            onDismiss: { showCover = false }
        ) {
            FullScreenImageViewer(media: media, dismiss: { showCover = false })
        }
#endif
    }
}

struct MutedVideoPlayer: View {
    let player: AVPlayer
    let autoPlay: Bool
    init(url: String, autoPlay: Bool = true) {
        self.player = AVPlayer(url: URL(string: url)!)
        self.player.isMuted = true
        self.autoPlay = autoPlay
    }
    var body: some View {
        VideoPlayer(player: player)
            .if(autoPlay) { view in
                view
                    .onAppear {
                        player.play()
                    }
                    .onDisappear {
                        player.pause()
                    }
            }
    }
}
