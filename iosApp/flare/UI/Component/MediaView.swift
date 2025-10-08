import SwiftUI
import TipKit
import KotlinSharedUI
import VideoPlayer
import AVFoundation

struct MediaView: View {
    let data: UiMedia
    var body: some View {
        switch onEnum(of: data) {
        case .image(let image):
            NetworkImage(data: image.previewUrl)
        case .video(let video):
            MediaVideoView(data: video)
        case .gif(let gif):
            NetworkImage(data: gif.url)
        case .audio(let audio):
            EmptyView()
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
            .overlay(alignment: .center) {
                if case .loading = videoState {
                    NetworkImage(data: data.thumbnailUrl)
                        .overlay {
                            ProgressView()
                                .progressViewStyle(.circular)
                                .tint(.white)
                                .scaleEffect(1.5)
                        }
                } else if case .idle = videoState {
                    NetworkImage(data: data.thumbnailUrl)
                        .overlay {
                            Image("fa-circle-play")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 50, height: 50)
                                .foregroundStyle(Color(.white))
                                .padding(8)
                                .background(.black, in: .rect(cornerRadius: 16))
                                .padding()
                        }
                } else if case .error = videoState {
                    NetworkImage(data: data.thumbnailUrl)
                        .overlay {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 50, height: 50)
                                .foregroundStyle(Color(.white))
                                .padding(8)
                                .background(.black, in: .rect(cornerRadius: 16))
                                .padding()
                        }
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
