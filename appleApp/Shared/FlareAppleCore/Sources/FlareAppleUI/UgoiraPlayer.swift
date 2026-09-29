import Combine
import FlareAppleCore
import ImageIO
@preconcurrency import KotlinSharedUI
import SwiftUI

@Observable @MainActor
public final class UgoiraPlaybackSession {
    public private(set) var image: CGImage?
    public var progress: Double { Double(presenter?.state.progress ?? 0) }
    public var failed: Bool { presenter?.state.failed ?? false }
    public private(set) var paused = false
    public var onFrame: ((CGImage) -> Void)?
    private var task: Task<Void, Never>?
    private var presenter: KotlinPresenter<UgoiraPresenterState>?
    private var subscription: AnyCancellable?
    private var media: UiMediaUgoira?
    private var generation = 0
    private var position = 0.0
    private var startedAt: Double?
    private var duration = 1.0

    public init() {}

    public func play(_ media: UiMediaUgoira) {
        guard presenter == nil else { return }
        self.media = media
        generation = MediaPlaybackMemory.shared.generation(for: media.url)
        paused = MediaPlaybackMemory.shared.paused(for: media.url)
        position = MediaPlaybackMemory.shared.position(for: media.url)
        let presenter = KotlinPresenter(presenter: UgoiraPresenter(media: media))
        self.presenter = presenter
        subscription = presenter.statePublisher.sink { [weak self] state in
            guard let self else { return }
            guard let animation = state.animation, !state.failed else {
                task?.cancel()
                task = nil
                return
            }
            if task == nil { render(animation, media: media, state: state) }
        }
        presenter.state.setActive(value: true)
    }

    private func render(_ animation: UgoiraAnimation, media: UiMediaUgoira, state: UgoiraPresenterState) {
        task = Task { [weak self] in
            guard let self else { return }
            do {
                try Task.checkCancellation()
                let frames = animation.frames
                let duration = Double(animation.durationMillis) / 1000
                self.duration = duration
                var start = ProcessInfo.processInfo.systemUptime - position
                startedAt = start
                var wasPaused = paused
                var decoded: [Int: CGImage] = [:]
                var displayed = -1
                while !Task.isCancelled {
                    if !paused, wasPaused { start = ProcessInfo.processInfo.systemUptime - position }
                    let elapsed = paused ? position : ProcessInfo.processInfo.systemUptime - start
                    if !paused {
                        position = elapsed.truncatingRemainder(dividingBy: duration)
                    }
                    wasPaused = paused
                    let index = Int(animation.frameIndex(positionMillis: Int64(position * 1000)))
                    if displayed != index {
                        let frame: CGImage
                        if let cached = decoded[index] {
                            frame = cached
                        } else {
                            let path = frames[index].file
                            frame = try await Task.detached(priority: .userInitiated) { try decodeUgoiraImage(path, maximumSize: 4096) }
                                .value
                        }
                        try Task.checkCancellation()
                        image = frame
                        onFrame?(frame)
                        displayed = index
                        decoded = [index: frame]
                        if !paused {
                            let next = (index + 1) % frames.count
                            let path = frames[next].file
                            decoded[next] = try await Task.detached(priority: .userInitiated) {
                                try decodeUgoiraImage(path, maximumSize: 4096)
                            }.value
                        }
                    }
                    if generation == MediaPlaybackMemory.shared.generation(for: media.url) {
                        MediaPlaybackMemory.shared.save(position, for: media.url)
                    }
                    let end = Double(animation.frameStartMillis(index: Int32(index)) + Int64(frames[index].delayMillis)) / 1000
                    let now = ProcessInfo.processInfo.systemUptime - start
                    let delay = ugoiraFrameDelay(frameEnd: end, duration: duration, selectedAt: elapsed, now: now)
                    try await Task.sleep(for: .seconds(paused ? 0.1 : delay))
                }
            } catch is CancellationError {
            } catch {
                if !Task.isCancelled { state.onDecodeFailure(value: animation) }
            }
        }
    }

    public func toggle() {
        guard let media else { return }
        if failed {
            image = nil
            presenter?.state.retry()
            return
        }
        let shouldPause = !paused
        detach(clearImage: false)
        MediaPlaybackMemory.shared.savePaused(shouldPause, for: media.url)
        play(media)
    }

    public func detach(clearImage: Bool = true) {
        if !paused, let startedAt {
            position = (ProcessInfo.processInfo.systemUptime - startedAt).truncatingRemainder(dividingBy: duration)
        }
        startedAt = nil
        if let media, generation == MediaPlaybackMemory.shared.generation(for: media.url) {
            MediaPlaybackMemory.shared.save(position, for: media.url)
        }
        task?.cancel()
        task = nil
        subscription?.cancel()
        subscription = nil
        presenter = nil
        if clearImage { image = nil }
    }
}

nonisolated func ugoiraFrameDelay(frameEnd: Double, duration: Double, selectedAt: Double, now: Double) -> Double {
    // Keep the selected frame's cycle even when decoding finishes in a later one.
    let cycleStart = selectedAt - selectedAt.truncatingRemainder(dividingBy: duration)
    return max(0.001, cycleStart + frameEnd - now)
}

nonisolated func decodeUgoiraImage(_ path: String, maximumSize: Int? = nil) throws -> CGImage {
    guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil),
        let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
        let width = properties[kCGImagePropertyPixelWidth] as? Int,
        let height = properties[kCGImagePropertyPixelHeight] as? Int,
        width > 0, height > 0, width <= 32768, height <= 32768, width * height <= 100_000_000
    else {
        throw CocoaError(.fileReadCorruptFile)
    }
    let image: CGImage?
    if let maximumSize {
        image = CGImageSourceCreateThumbnailAtIndex(
            source, 0,
            [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceThumbnailMaxPixelSize: maximumSize,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceShouldCacheImmediately: true,
            ] as CFDictionary)
    } else {
        image = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
    }
    guard let image else { throw CocoaError(.fileReadCorruptFile) }
    return image
}

public struct UgoiraPlayer: View {
    let media: UiMediaUgoira
    let active: Bool
    let contentMode: ContentMode
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.videoPlaybackPresentation) private var presentation
    @State private var fallbackPresentation = VideoPlaybackPresentation()
    @State private var session = UgoiraPlaybackSession()

    public init(media: UiMediaUgoira, active: Bool = true, contentMode: ContentMode = .fit) {
        self.media = media
        self.active = active
        self.contentMode = contentMode
    }

    public var body: some View {
        ZStack(alignment: .bottomLeading) {
            UgoiraFrameView(media: media, session: session, contentMode: contentMode)
            if session.failed || session.image != nil {
                Button {
                    session.toggle()
                } label: {
                    Image(systemName: session.failed ? "arrow.clockwise" : session.paused ? "play.fill" : "pause.fill")
                        .padding(12).background(.black.opacity(0.6), in: Capsule()).foregroundStyle(.white)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(
                    Text(
                        LocalizedStringKey(session.failed ? "Retry" : session.paused ? "ugoira_play" : "ugoira_pause"),
                        bundle: FlareAppleUILocalization.bundle)
                )
                .padding()
            }
        }
        .onAppear {
            if presentation == nil {
                fallbackPresentation.setSuspended(scenePhase != .active)
                fallbackPresentation.begin()
            }
            updatePlayback()
        }
        .onChange(of: active) { _, _ in updatePlayback() }
        .onChange(of: scenePhase) { _, phase in
            if presentation == nil { fallbackPresentation.setSuspended(phase != .active) }
        }
        .onDisappear {
            (presentation ?? fallbackPresentation).releaseFrames(owner: session)
            if presentation == nil { fallbackPresentation.end() }
        }
    }
    private func updatePlayback() {
        if active {
            (presentation ?? fallbackPresentation).updateFrames(owner: session, play: { session.play(media) }, stop: { session.detach() })
        } else {
            (presentation ?? fallbackPresentation).releaseFrames(owner: session)
        }
    }

}

struct UgoiraFrameView: View {
    let media: UiMediaUgoira
    let session: UgoiraPlaybackSession
    var contentMode: ContentMode = .fill
    var body: some View {
        Color.clear.overlay {
            if let image = session.image {
                Image(decorative: image, scale: 1).resizable().aspectRatio(contentMode: contentMode)
            } else {
                NetworkImage(data: media.previewUrl, customHeader: media.customHeaders, contentMode: contentMode)
                if !session.failed {
                    ProgressView(value: session.progress).padding().accessibilityLabel(
                        Text("ugoira_loading", bundle: FlareAppleUILocalization.bundle))
                }
            }
        }.clipped().accessibilityLabel(media.accessibleDescription)
    }
}

public struct GalleryMedia: View {
    let media: any UiMedia
    let contentMode: ContentMode
    @State private var entered = false
    public init(media: any UiMedia, contentMode: ContentMode = .fit) {
        self.media = media
        self.contentMode = contentMode
    }
    public var body: some View {
        Group {
            if let animation = media as? UiMediaUgoira {
                if entered { UgoiraPlayer(media: animation, contentMode: contentMode) }
            } else {
                NetworkImage(data: media.url, customHeader: media.customHeaders, contentMode: contentMode)
            }
        }.onAppear {
            if !entered {
                MediaPlaybackMemory.shared.reset(media.url)
                entered = true
            }
        }
    }
}
