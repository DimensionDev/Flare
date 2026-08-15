import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import AVFoundation

#if canImport(VideoPlayer)
import VideoPlayer
#endif

public struct MediaView: View {
    private let data: UiMedia
    private let allowsAutoplay: Bool

    public init(data: UiMedia, allowsAutoplay: Bool = true) {
        self.data = data
        self.allowsAutoplay = allowsAutoplay
    }

    public var body: some View {
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
                MediaVideoView(data: video, allowsAutoplay: allowsAutoplay)
            case .gif(let gif):
                Color.gray
                    .overlay {
                        NetworkImage(data: gif.url, customHeader: gif.customHeaders)
                            .allowsHitTesting(false)
                    }
                    .clipped()
            case .audio:
                EmptyView()
            }
        }
    }
}

public struct MediaVideoView: View {
    @Environment(\.timelineAppearance.videoAutoplay) private var videoAutoplay
    @Environment(\.networkKind) private var networkKind
    @Environment(\.isScrolling) private var isScrolling
    @Environment(\.isScrollingState) private var isScrollingState
    @State private var play = false
    @State private var videoState: MediaVideoState = .idle
    @State private var time: CMTime = .zero
    @State private var isAppeared = false
    #if os(macOS)
    @State private var macPlayer: AVQueuePlayer?
    @State private var macPlayerURL: URL?
    @State private var macPlayerLooper: AVPlayerLooper?
    #endif
    private let data: UiMediaVideo
    private let allowsAutoplay: Bool

    public init(data: UiMediaVideo, allowsAutoplay: Bool = true) {
        self.data = data
        self.allowsAutoplay = allowsAutoplay
    }

    private var effectiveIsScrolling: Bool {
        isScrollingState?.isScrolling ?? isScrolling
    }

    private var canAutoplay: Bool {
        guard allowsAutoplay else { return false }
        switch videoAutoplay {
        case .always:
            return true
        case .wifi:
            return networkKind == .wifi
        case .never:
            return false
        }
    }

    public var body: some View {
        #if os(iOS)
        Color.gray
            .overlay {
                NetworkImage(data: data.thumbnailUrl, customHeader: data.customHeaders)
                    .allowsHitTesting(false)
            }
            .clipped()
            .overlay {
                if canAutoplay {
                    player
                }
            }
            .overlay(alignment: .bottomLeading) {
                statusOverlay
            }
        #elseif os(macOS)
        macContent
        #else
        EmptyView()
        #endif
    }

    @ViewBuilder
    private var player: some View {
        #if canImport(VideoPlayer)
        if let videoURL = URL(string: data.url) {
            VideoPlayer(url: videoURL, play: $play, time: $time)
                .mute(true)
                .autoReplay(true)
                .onStateChanged { state in
                    switch state {
                    case .playing(let duration):
                        videoState = .playing(duration)
                    case .loading:
                        videoState = .loading
                    case .paused:
                        videoState = .idle
                    case .error(let error):
                        videoState = .error(error)
                    }
                }
                .contentMode(.scaleAspectFill)
                .onChange(of: effectiveIsScrolling) { _, newValue in
                    play = !newValue && isAppeared && canAutoplay
                }
                .onAppear {
                    isAppeared = true
                    play = !effectiveIsScrolling && canAutoplay
                }
                .onDisappear {
                    isAppeared = false
                    play = false
                    videoState = .idle
                }
                .allowsHitTesting(false)
        }
        #else
        EmptyView()
        #endif
    }

    #if os(macOS)
    @ViewBuilder
    private var macContent: some View {
        Color.gray
            .overlay {
                NetworkImage(data: data.thumbnailUrl, customHeader: data.customHeaders)
                    .allowsHitTesting(false)
            }
            .clipped()
            .overlay {
                if let macPlayer {
                    MacAVPlayerView(player: macPlayer, videoGravity: .resizeAspectFill, showsControls: false)
                        .allowsHitTesting(false)
                }
            }
            .overlay(alignment: .bottomLeading) {
                statusOverlay
            }
            .onAppear {
                isAppeared = true
                updateMacPlayback()
            }
            .onChange(of: effectiveIsScrolling) { _, _ in
                updateMacPlayback()
            }
            .onChange(of: canAutoplay) { _, _ in
                updateMacPlayback()
            }
            .onChange(of: data.url) { _, _ in
                updateMacPlayback()
            }
            .task(id: play) {
                guard play else { return }
                while !Task.isCancelled {
                    refreshMacState()
                    try? await Task.sleep(for: .milliseconds(250))
                }
            }
            .onDisappear {
                isAppeared = false
                play = false
                resetMacPlayer()
                videoState = .idle
            }
    }

    private func configureMacPlayerIfNeeded() {
        guard let videoURL = URL(string: data.url) else {
            resetMacPlayer()
            videoState = .error(URLError(.badURL))
            return
        }

        if macPlayerURL == videoURL, let macPlayer {
            macPlayer.isMuted = true
            macPlayer.actionAtItemEnd = .advance
            return
        }

        resetMacPlayer()
        let player = AVQueuePlayer()
        player.isMuted = true
        player.actionAtItemEnd = .advance
        let item = AVPlayerItem(url: videoURL)
        macPlayer = player
        macPlayerLooper = AVPlayerLooper(player: player, templateItem: item)
        macPlayerURL = videoURL
        time = .zero
        videoState = .loading
    }

    private func updateMacPlayback() {
        guard isAppeared, canAutoplay else {
            play = false
            resetMacPlayer()
            videoState = .idle
            return
        }

        guard !effectiveIsScrolling else {
            play = false
            macPlayer?.pause()
            videoState = .idle
            return
        }

        configureMacPlayerIfNeeded()
        guard let macPlayer else { return }
        play = true
        macPlayer.playImmediately(atRate: 1)
    }

    private func refreshMacState() {
        guard let macPlayer, let item = macPlayer.currentItem, macPlayerURL != nil else {
            return
        }

        if let error = item.error {
            videoState = .error(error)
            play = false
            return
        }

        let currentTime = macPlayer.currentTime()
        if currentTime.seconds.isFinite {
            time = currentTime
        }

        let duration = item.duration.seconds
        switch item.status {
        case .readyToPlay:
            switch macPlayer.timeControlStatus {
            case .playing where macPlayer.rate != 0 && duration.isFinite:
                videoState = .playing(duration)
            case .playing:
                videoState = play ? .loading : .idle
            case .waitingToPlayAtSpecifiedRate:
                videoState = .loading
            case .paused:
                videoState = play ? .loading : .idle
            @unknown default:
                videoState = play ? .loading : .idle
            }
        case .failed:
            videoState = .error(item.error ?? URLError(.cannotDecodeContentData))
            play = false
        case .unknown:
            videoState = play ? .loading : .idle
        @unknown default:
            videoState = play ? .loading : .idle
        }
    }

    private func resetMacPlayer() {
        macPlayer?.pause()
        macPlayerLooper = nil
        macPlayer = nil
        macPlayerURL = nil
    }

    private func formatMacRemainingTime(duration: Double) -> String {
        guard duration.isFinite, duration > 0 else {
            return "0:00"
        }
        let currentSeconds = time.seconds.isFinite ? time.seconds : 0
        let remainingSeconds = max(Int((duration - currentSeconds).rounded(.down)), 0)
        return String(format: "%d:%02d", remainingSeconds / 60, remainingSeconds % 60)
    }
    #endif

    @ViewBuilder
    private var statusOverlay: some View {
        switch videoState {
        case .idle:
            Image(fontAwesome: .circlePlay)
                .mediaVideoBadgeStyle()
        case .loading:
            ProgressView()
                .tint(.white)
                .mediaVideoBadgeStyle()
        case .playing(let duration):
            #if os(macOS)
            Text(formatMacRemainingTime(duration: duration))
                .font(.caption)
                .foregroundStyle(.white)
                .mediaVideoBadgeStyle()
            #else
            Text(Date(timeIntervalSinceNow: duration - time.seconds), style: .timer)
                .font(.caption)
                .foregroundStyle(.white)
                .mediaVideoBadgeStyle()
            #endif
        case .error:
            Image(systemName: "exclamationmark.triangle.fill")
                .mediaVideoBadgeStyle()
        }
    }
}

private enum MediaVideoState {
    case idle
    case loading
    case playing(Double)
    case error(any Error)
}

private extension View {
    func mediaVideoBadgeStyle() -> some View {
        padding(8)
            .background(.black, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding()
            .foregroundStyle(.white)
    }
}
