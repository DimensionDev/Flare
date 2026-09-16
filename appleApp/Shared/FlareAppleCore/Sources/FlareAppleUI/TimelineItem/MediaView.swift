import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import AVFoundation

#if os(iOS)
import UIKit
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
        .accessibilityLabel(Text(verbatim: data.accessibleDescription))
    }
}

public struct MediaVideoView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.timelineAppearance.videoAutoplay) private var videoAutoplay
    @Environment(\.networkKind) private var networkKind
    @Environment(\.timelinePlaybackCoordinator) private var timelinePlayback
    @Environment(\.timelineCarouselItem) private var carouselItem
    @Environment(\.isMultipleColumn) private var isMultipleColumn
    @Environment(\.timelinePlaybackViewport) private var timelinePlaybackViewport
    @State private var fallbackPlayback = TimelinePlaybackCoordinator()
    @State private var player = VideoPlaybackSession()
    @State private var id = UUID().uuidString
    @State private var geometry = InlineVideoGeometry()
    @State private var appeared = false
    private let data: UiMediaVideo
    private let allowsAutoplay: Bool

    public init(data: UiMediaVideo, allowsAutoplay: Bool = true) {
        self.data = data
        self.allowsAutoplay = allowsAutoplay
    }

    private var playback: TimelinePlaybackCoordinator { timelinePlayback ?? fallbackPlayback }

    private var canAutoplay: Bool {
        guard allowsAutoplay else { return false }
        switch videoAutoplay {
        case .always: return true
        case .wifi: return networkKind == .wifi
        case .never: return false
        }
    }

    public var body: some View {
        Color.gray
            .overlay {
                NetworkImage(data: data.thumbnailUrl, customHeader: data.customHeaders)
                    .allowsHitTesting(false)
            }
            .clipped()
            .overlay {
                if let avPlayer = player.player {
                    #if os(macOS)
                    MacAVPlayerView(player: avPlayer, videoGravity: .resizeAspectFill, showsControls: false,
                                    canDisplayFrame: player.hasRestoredPosition)
                        .id(data.url)
                        .allowsHitTesting(false)
                    #elseif os(iOS)
                    InlineAVPlayerView(player: avPlayer, canDisplayFrame: player.hasRestoredPosition)
                        .id(data.url)
                        .allowsHitTesting(false)
                    #endif
                }
            }
            .overlay(alignment: .bottomLeading) { statusOverlay }
            .onGeometryChange(for: InlineVideoGeometry.self) { [timelinePlaybackViewport, isMultipleColumn] proxy in
                let bounds = CGRect(origin: .zero, size: proxy.size)
                let scrollBounds = proxy.bounds(of: .scrollView(axis: .vertical)) ?? bounds
                let globalBounds = proxy.frame(in: .global)
                let viewport = timelinePlaybackViewport?.offsetBy(dx: -globalBounds.minX, dy: -globalBounds.minY) ?? scrollBounds
                let vertical = scrollBounds.intersection(viewport)
                let horizontal = proxy.bounds(of: .scrollView(axis: .horizontal)) ?? bounds
                let visible = bounds.intersection(vertical).intersection(horizontal)
                return InlineVideoGeometry(
                    visible: !visible.isEmpty,
                    horizontalFraction: bounds.width > 0 ? max(0, visible.width) / bounds.width : 0,
                    distance: TimelineAutoplayPolicy.centerDistance(of: bounds, in: viewport, multipleColumns: isMultipleColumn),
                    verticalOffset: scrollBounds.minY
                )
            } action: { value in
                if #unavailable(iOS 18.0, macOS 15.0) {
                    if let oldOffset = geometry.verticalOffset, oldOffset != value.verticalOffset {
                        playback.moved(source: "vertical", vertical: true)
                    }
                }
                geometry = value
                updateCandidate()
            }
            .onAppear {
                if timelinePlayback == nil { fallbackPlayback.setSuspended(scenePhase != .active) }
                appeared = true
                registerPlayer()
                updateCandidate()
            }
            .onChange(of: canAutoplay) { _, _ in updateCandidate() }
            .onChange(of: scenePhase) { _, phase in
                if timelinePlayback == nil { fallbackPlayback.setSuspended(phase != .active) }
            }
            .onChange(of: carouselItem) { _, _ in updateCandidate() }
            .onChange(of: data.url) { _, _ in
                playback.remove(id: id)
                player.detach()
                registerPlayer()
                updateCandidate()
            }
            .onDisappear {
                appeared = false
                playback.remove(id: id)
                player.detach()
                if timelinePlayback == nil { fallbackPlayback.setSuspended(true) }
            }
    }

    private func registerPlayer() {
        let url = data.url
        let coordinator = playback
        let model = player
        coordinator.register(id: id) { playing in
            if playing {
                model.play(url: url)
            } else {
                model.detach()
            }
        }
    }

    private func updateCandidate() {
        guard appeared else { return }
        playback.update(.init(
            id: id,
            groupID: carouselItem?.groupID,
            isVisible: geometry.visible && canAutoplay,
            isSelected: carouselItem?.isSelected ?? true,
            canStart: canAutoplay && (carouselItem?.isCarousel != true || geometry.horizontalFraction >= 0.6),
            distance: geometry.distance,
            mediaURL: data.url
        ))
    }

    @ViewBuilder
    private var statusOverlay: some View {
        switch player.state {
        case .idle, .paused:
            Image(fontAwesome: .circlePlay).mediaVideoBadgeStyle()
        case .loading:
            ProgressView().tint(.white).mediaVideoBadgeStyle()
        case .playing(let duration):
            let remaining = max(Int((duration - player.position).rounded(.down)), 0)
            Text(String(format: "%d:%02d", remaining / 60, remaining % 60))
                .font(.caption)
                .foregroundStyle(.white)
                .mediaVideoBadgeStyle()
        case .error:
            Image(systemName: "exclamationmark.triangle.fill").mediaVideoBadgeStyle()
        }
    }
}

private nonisolated struct InlineVideoGeometry: Equatable, Sendable {
    var visible = false
    var horizontalFraction: CGFloat = 0
    var distance: Double = 0
    var verticalOffset: CGFloat?
}

#if os(iOS)
struct InlineAVPlayerView: UIViewRepresentable {
    let player: AVPlayer
    var videoGravity: AVLayerVideoGravity = .resizeAspectFill
    var canDisplayFrame = true
    var onReady: () -> Void = {}

    func makeUIView(context: Context) -> VideoPlaybackSurfaceView {
        let view = VideoPlaybackSurfaceView()
        view.onReady = onReady
        view.canDisplayFrame = canDisplayFrame
        view.playerLayer.videoGravity = videoGravity
        view.player = player
        return view
    }

    func updateUIView(_ view: VideoPlaybackSurfaceView, context: Context) {
        view.onReady = onReady
        view.canDisplayFrame = canDisplayFrame
        view.player = player
        view.playerLayer.videoGravity = videoGravity
    }

    static func dismantleUIView(_ view: VideoPlaybackSurfaceView, coordinator: ()) {
        view.onReady = {}
        view.player = nil
    }
}
#endif

private extension View {
    func mediaVideoBadgeStyle() -> some View {
        padding(8)
            .background(.black, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding()
            .foregroundStyle(.white)
    }
}
