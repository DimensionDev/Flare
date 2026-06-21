import SwiftUI
import KotlinSharedUI
import AppleFontAwesome
import AVFoundation
import Combine

#if canImport(VideoPlayer)
import VideoPlayer
#endif

public struct MediaView: View {
    private let data: UiMedia

    public init(data: UiMedia) {
        self.data = data
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
                MediaVideoView(data: video)
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
    @State private var macPlayer = AVPlayer()
    @State private var macPlayerURL: URL?
    @State private var macEndObserver: NSObjectProtocol?
    #endif
    private let data: UiMediaVideo

    public init(data: UiMediaVideo) {
        self.data = data
    }

    private var effectiveIsScrolling: Bool {
        isScrollingState?.isScrolling ?? isScrolling
    }

    private func canPlay() -> Bool {
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
                player
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
                    if !newValue, !play, isAppeared, canPlay() {
                        play = true
                    }
                }
                .onAppear {
                    isAppeared = true
                    if !effectiveIsScrolling, canPlay() {
                        play = true
                    }
                }
                .onDisappear {
                    isAppeared = false
                    play = false
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
                if macPlayerURL != nil {
                    MacAVPlayerView(player: macPlayer, videoGravity: .resizeAspectFill, showsControls: false)
                        .allowsHitTesting(false)
                }
            }
            .overlay(alignment: .bottomLeading) {
                statusOverlay
            }
            .onAppear {
                isAppeared = true
                configureMacPlayerIfNeeded()
                updateMacPlayback()
            }
            .onChange(of: effectiveIsScrolling) { _, _ in
                updateMacPlayback()
            }
            .onChange(of: networkKind) { _, _ in
                updateMacPlayback()
            }
            .onChange(of: data.url) { _, _ in
                configureMacPlayerIfNeeded()
                updateMacPlayback()
            }
            .onReceive(Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()) { _ in
                refreshMacState()
            }
            .onDisappear {
                isAppeared = false
                play = false
                macPlayer.pause()
                removeMacEndObserver()
            }
    }

    private func configureMacPlayerIfNeeded() {
        guard let videoURL = URL(string: data.url) else {
            removeMacEndObserver()
            macPlayer.pause()
            macPlayer.replaceCurrentItem(with: nil)
            macPlayerURL = nil
            videoState = .error(URLError(.badURL))
            return
        }

        macPlayer.isMuted = true
        macPlayer.actionAtItemEnd = .none

        guard macPlayerURL != videoURL else {
            return
        }

        removeMacEndObserver()
        let item = AVPlayerItem(url: videoURL)
        macPlayer.replaceCurrentItem(with: item)
        macPlayerURL = videoURL
        time = .zero
        videoState = .loading

        let player = macPlayer
        macEndObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { _ in
            MacAVPlayerPlayback.replay(player, rate: 1)
        }
    }

    private func updateMacPlayback() {
        configureMacPlayerIfNeeded()
        if !play, isAppeared, !effectiveIsScrolling, canPlay() {
            play = true
        }

        if play {
            macPlayer.playImmediately(atRate: 1)
        } else {
            macPlayer.pause()
        }
    }

    private func refreshMacState() {
        guard let item = macPlayer.currentItem, macPlayerURL != nil else {
            return
        }

        if let error = item.error {
            videoState = .error(error)
            return
        }

        let currentTime = macPlayer.currentTime()
        if currentTime.seconds.isFinite {
            time = currentTime
        }

        let duration = item.duration.seconds
        switch item.status {
        case .readyToPlay:
            if macPlayer.rate != 0, duration.isFinite {
                videoState = .playing(duration)
            } else if play {
                videoState = .loading
            } else {
                videoState = .idle
            }
        case .failed:
            videoState = .error(item.error ?? URLError(.cannotDecodeContentData))
        case .unknown:
            videoState = play ? .loading : .idle
        @unknown default:
            videoState = play ? .loading : .idle
        }
    }

    private func removeMacEndObserver() {
        if let macEndObserver {
            NotificationCenter.default.removeObserver(macEndObserver)
            self.macEndObserver = nil
        }
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
            Text(Date(timeIntervalSinceNow: duration - time.seconds), style: .timer)
                .font(.caption)
                .foregroundStyle(.white)
                .mediaVideoBadgeStyle()
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
