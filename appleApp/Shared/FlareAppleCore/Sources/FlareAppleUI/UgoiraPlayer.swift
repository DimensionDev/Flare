import ImageIO
@preconcurrency import KotlinSharedUI
import SwiftUI

@Observable @MainActor
public final class UgoiraPlaybackSession {
    public private(set) var image: CGImage?
    public private(set) var progress = 0.0
    public private(set) var failed = false
    public private(set) var paused = false
    public var onFrame: ((CGImage) -> Void)?
    private var task: Task<Void, Never>?
    private var media: UiMediaUgoira?
    private var generation = 0
    private var position = 0.0
    private var startedAt: Double?
    private var duration = 1.0

    public init() {}

    public func play(_ media: UiMediaUgoira) {
        guard task == nil else { return }
        self.media = media
        generation = MediaPlaybackMemory.shared.generation(for: media.url)
        paused = MediaPlaybackMemory.shared.paused(for: media.url)
        position = MediaPlaybackMemory.shared.position(for: media.url)
        failed = false
        task = Task { [weak self] in
            guard let self else { return }
            do {
                let onProgress: @Sendable (KotlinFloat) -> Void = { [weak self] value in
                    let fraction = value.doubleValue
                    Task { @MainActor [weak self] in self?.progress = fraction }
                }
                let animation = try await UgoiraStore.shared.load(media: media, onProgress: onProgress)
                defer { UgoiraStore.shared.release(animation: animation) }
                try Task.checkCancellation()
                let frames = animation.frames
                let duration = Double(animation.durationMillis) / 1000
                self.duration = duration
                var start = ProcessInfo.processInfo.systemUptime - position
                startedAt = start
                var wasPaused = paused
                var decoded: [Int: CGImage] = [:]
                var displayed = -1
                do {
                    while !Task.isCancelled {
                        if !paused {
                            if wasPaused { start = ProcessInfo.processInfo.systemUptime - position }
                            position = (ProcessInfo.processInfo.systemUptime - start).truncatingRemainder(dividingBy: duration)
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
                        let now = (ProcessInfo.processInfo.systemUptime - start).truncatingRemainder(dividingBy: duration)
                        try await Task.sleep(for: .seconds(paused ? 0.1 : max(0.001, end - now)))
                    }
                } catch {
                    if !Task.isCancelled { try? await UgoiraStore.shared.invalidate(animation: animation) }
                    throw error
                }
            } catch is CancellationError {
            } catch {
                if !Task.isCancelled { failed = true }
            }
        }
    }

    public func toggle() {
        guard let media else { return }
        if failed {
            detach()
            play(media)
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
        if clearImage { image = nil }
    }
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
