import SwiftUI
import KotlinSharedUI
import AVFoundation

#if canImport(VideoPlayer)
import VideoPlayer
#endif

public enum VideoState {
    case idle
    case loading
    case playing(Double)
    case paused(Double)
    case error(any Error)
}

public struct StatusMediaVideoView: View {
    @Binding private var play: Bool
    @Binding private var videoState: VideoState
    @Binding private var time: CMTime
    private let data: UiMediaVideo

    public init(
        data: UiMediaVideo,
        play: Binding<Bool>,
        videoState: Binding<VideoState>,
        time: Binding<CMTime>
    ) {
        self.data = data
        self._play = play
        self._videoState = videoState
        self._time = time
    }

    public var body: some View {
        Color.clear
            .overlay {
                if case .idle = videoState {
                    NetworkImage(data: data.thumbnailUrl, customHeader: data.customHeaders)
                        .scaledToFit()
                        .allowsHitTesting(false)
                } else {
                    EmptyView()
                }
            }
            .clipped()
            .overlay {
                player
            }
    }

    @ViewBuilder
    private var player: some View {
        #if canImport(VideoPlayer)
        if let videoURL = URL(string: data.url) {
            VideoPlayer(url: videoURL, play: $play, time: $time)
                .mute(false)
                .autoReplay(true)
                .onStateChanged { state in
                    switch state {
                    case .playing(let duration):
                        videoState = .playing(duration)
                    case .loading:
                        videoState = .loading
                    case .paused:
                        if case .playing(let duration) = videoState {
                            videoState = .paused(duration)
                        } else if case .paused(let duration) = videoState {
                            videoState = .paused(duration)
                        } else {
                            videoState = .idle
                        }
                    case .error(let error):
                        videoState = .error(error)
                    }
                }
                .contentMode(.scaleAspectFit)
                .allowsHitTesting(false)
        }
        #else
        EmptyView()
        #endif
    }
}
