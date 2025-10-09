import SwiftUI
import TipKit
import KotlinSharedUI
import VideoPlayer
import AVFoundation

struct MediaView: View {
    let data: UiMedia
    let expandToFullSize: Bool
    
    init(data: UiMedia, expandToFullSize: Bool = false) {
        self.data = data
        self.expandToFullSize = expandToFullSize
    }
    
    var body: some View {
        switch onEnum(of: data) {
        case .image(let image):
            AdaptiveSizeMediaContainerView(expandToFullSize: expandToFullSize) {
                NetworkImage(data: image.previewUrl)
            }
        case .video(let video):
            MediaVideoView(data: video, expandToFullSize: expandToFullSize)
        case .gif(let gif):
            AdaptiveSizeMediaContainerView(expandToFullSize: expandToFullSize) {
                NetworkImage(data: gif.url)
            }
        case .audio(let audio):
            EmptyView()
        }
    }
}

struct AdaptiveSizeMediaContainerView<Content: View>: View {
    let expandToFullSize: Bool
    @ViewBuilder let content: () -> Content
    var body: some View {
        if expandToFullSize {
            content()
        } else {
            Color.gray
                .opacity(0.2)
                .overlay {
                    content()
                        .allowsHitTesting(false)
                }
                .clipped()
        }
    }
}

struct MediaVideoView: View {
    @Environment(\.themeSettings) private var themeSettings
    @Environment(\.networkKind) private var networkKind
    @Environment(\.isScrolling) private var isScrolling
    @State private var play: Bool = false
    @State private var videoState: VideoState = .idle
    @State private var time: CMTime = .zero
    @State private var isAppeared: Bool = false
    let data: UiMediaVideo
    let expandToFullSize: Bool
    
    func canPlay() -> Bool {
        switch themeSettings.appearanceSettings.videoAutoplay {
        case .always:
            return true
        case .wifi:
            return networkKind == .wifi
        case .never:
            return false
        }
    }
    
    var body: some View {
        AdaptiveSizeMediaContainerView(expandToFullSize: expandToFullSize) {
            NetworkImage(data: data.thumbnailUrl)
        }
        .overlay {
            VideoPlayer(url: .init(string: data.url)!, play: $play, time: $time)
                .mute(true)
                .autoReplay(true)
                .onStateChanged { state in
                    switch state {
                    case .playing(let duration): videoState = .playing(duration)
                    case .loading: videoState = .loading
                    case .paused: videoState = .idle
                    case .error(let error): videoState = .error(error)
                    }
                }
                .contentMode(.scaleAspectFill)
                .onChange(of: isScrolling, { oldValue, newValue in
                    if !newValue, !play, isAppeared, canPlay() {
                        play = true
                    }
                })
                .onAppear {
                    isAppeared = true
                    if !isScrolling, canPlay() {
                        play = true
                    }
                }
                .onDisappear {
                    isAppeared = false
                    play = false
                }
                .allowsHitTesting(false)
        }
        .overlay(alignment: .bottomLeading) {
            switch videoState {
            case .idle:
                Image("fa-circle-play")
                    .foregroundStyle(Color(.white))
                    .padding(8)
                    .background(.black, in: .rect(cornerRadius: 16))
                    .padding()
            case .loading:
                ProgressView()
                    .tint(.white)
                    .padding(8)
                    .background(.black, in: .rect(cornerRadius: 16))
                    .padding()
            case .playing(let duration):
                Text(
                    Date(timeIntervalSinceNow: duration - time.seconds),
                    style: .timer
                )
                .font(.caption)
                .foregroundStyle(Color(.white))
                .padding(8)
                .background(.black, in: .rect(cornerRadius: 16))
                .padding()
            case .error:
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Color(.white))
                    .padding(8)
                    .background(.black, in: .rect(cornerRadius: 16))
                    .padding()
            }
        }
    }
    
    enum VideoState {
        case idle
        case loading
        case playing(Double)
        case error(Error)
    }
}
