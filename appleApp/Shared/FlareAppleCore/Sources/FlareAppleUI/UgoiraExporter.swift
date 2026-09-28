import AVFoundation
import CoreGraphics
import ImageIO
@preconcurrency import KotlinSharedUI
import SwiftUI

nonisolated struct UgoiraExportFrame: Sendable {
    let path: String
    let delay: Int64
}
nonisolated enum UgoiraExportError: Error { case unsupportedResolution, encodingFailed }

nonisolated enum UgoiraEncoder {
    static func encode(
        frames: [UgoiraExportFrame], destination: URL, smaller: Bool,
        progress: @escaping @Sendable (Double) async -> Void
    ) async throws {
        guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: frames[0].path) as CFURL, nil),
            let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
            let originalWidth = properties[kCGImagePropertyPixelWidth] as? Int,
            let originalHeight = properties[kCGImagePropertyPixelHeight] as? Int,
            originalWidth > 0, originalHeight > 0,
            originalWidth <= 32768, originalHeight <= 32768, originalWidth * originalHeight <= 100_000_000
        else { throw CocoaError(.fileReadCorruptFile) }
        let scale = smaller ? min(1, 1920 / Double(max(originalWidth, originalHeight))) : 1
        let contentWidth = max(1, Int(Double(originalWidth) * scale))
        let contentHeight = max(1, Int(Double(originalHeight) * scale))
        let width = (contentWidth + 1) / 2 * 2
        let height = (contentHeight + 1) / 2 * 2
        let frameRate = min(60, max(1, 1000.0 * Double(frames.count) / Double(frames.reduce(0) { $0 + $1.delay })))
        let settings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: width, AVVideoHeightKey: height,
            AVVideoCompressionPropertiesKey: [
                AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
                AVVideoAverageBitRateKey: min(100_000_000, max(4_000_000, Double(width * height) * frameRate * 0.25)),
                AVVideoAllowFrameReorderingKey: false,
                AVVideoMaxKeyFrameIntervalKey: max(1, Int(frameRate)),
            ],
        ]
        let writer = try AVAssetWriter(outputURL: destination, fileType: .mp4)
        var finished = false
        defer {
            if !finished {
                writer.cancelWriting()
                try? FileManager.default.removeItem(at: destination)
            }
        }
        guard writer.canApply(outputSettings: settings, forMediaType: .video) else { throw UgoiraExportError.unsupportedResolution }
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: input,
            sourcePixelBufferAttributes: [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB,
                kCVPixelBufferWidthKey as String: width, kCVPixelBufferHeightKey as String: height,
                kCVPixelBufferCGImageCompatibilityKey as String: true,
                kCVPixelBufferCGBitmapContextCompatibilityKey as String: true,
            ])
        guard writer.canAdd(input) else { throw UgoiraExportError.unsupportedResolution }
        writer.add(input)
        guard writer.startWriting() else { throw UgoiraExportError.unsupportedResolution }
        writer.startSession(atSourceTime: .zero)
        var timestamp: Int64 = 0
        for (index, frame) in frames.enumerated() {
            try Task.checkCancellation()
            let deadline = ContinuousClock.now.advanced(by: .seconds(60))
            while !input.isReadyForMoreMediaData {
                guard writer.status == .writing, ContinuousClock.now < deadline else { throw UgoiraExportError.encodingFailed }
                try await Task.sleep(for: .milliseconds(5))
            }
            try autoreleasepool {
                let image = try decodeUgoiraImage(frame.path, maximumSize: smaller ? 1920 : nil)
                var buffer: CVPixelBuffer?
                guard let pool = adaptor.pixelBufferPool,
                    CVPixelBufferPoolCreatePixelBuffer(nil, pool, &buffer) == kCVReturnSuccess, let buffer
                else {
                    throw UgoiraExportError.encodingFailed
                }
                CVPixelBufferLockBaseAddress(buffer, [])
                defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
                guard
                    let context = CGContext(
                        data: CVPixelBufferGetBaseAddress(buffer), width: width, height: height,
                        bitsPerComponent: 8, bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),
                        space: CGColorSpace(name: CGColorSpace.sRGB)!,
                        bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue)
                else {
                    throw UgoiraExportError.encodingFailed
                }
                context.setFillColor(CGColor(gray: 1, alpha: 1))
                context.fill(CGRect(x: 0, y: 0, width: width, height: height))
                context.interpolationQuality = .high
                context.draw(image, in: CGRect(x: 0, y: height - contentHeight, width: contentWidth, height: contentHeight))
                guard adaptor.append(buffer, withPresentationTime: CMTime(value: timestamp, timescale: 1000)) else {
                    throw UgoiraExportError.unsupportedResolution
                }
            }
            timestamp += frame.delay
            await progress(Double(index + 1) / Double(frames.count))
        }
        // Explicitly end at the last frame's end, not at its presentation timestamp.
        writer.endSession(atSourceTime: CMTime(value: timestamp, timescale: 1000))
        input.markAsFinished()
        await writer.finishWriting()
        try Task.checkCancellation()
        guard writer.status == .completed else { throw UgoiraExportError.encodingFailed }
        finished = true
    }
}

/// Jobs outlive individual screens; only complete files reach Photos or the save dialog.
@Observable @MainActor
public final class UgoiraExporter {
    public static let shared = UgoiraExporter()
    public private(set) var progress: Double?
    public private(set) var needsSmaller = false
    public private(set) var failed = false
    private var tail: Task<Void, Never>?
    private var active: Task<Void, Never>?
    private var resolutionChoice: CheckedContinuation<Bool, Never>?

    public func save(
        _ media: UiMediaUgoira, fileName: String,
        write: @escaping @MainActor (URL) async throws -> Void,
        completion: @escaping @MainActor (Bool) -> Void = { _ in }
    ) {
        let previous = tail
        let task = Task { @MainActor [self] in
            await previous?.value
            progress = 0
            failed = false
            do {
                let onProgress: @Sendable (KotlinFloat) -> Void = { [weak self] value in
                    let fraction = value.doubleValue
                    Task { @MainActor [weak self] in self?.progress = fraction * 0.5 }
                }
                let animation = try await UgoiraStore.shared.load(media: media, onProgress: onProgress)
                defer { UgoiraStore.shared.release(animation: animation) }
                let frames = animation.frames.map { UgoiraExportFrame(path: $0.file, delay: Int64($0.delayMillis)) }
                let directory = FileManager.default.temporaryDirectory.appendingPathComponent(
                    "ugoira-\(UUID().uuidString)", isDirectory: true)
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                defer { try? FileManager.default.removeItem(at: directory) }
                let url = directory.appendingPathComponent(fileName)
                let update: @Sendable (Double) async -> Void = { [weak self] value in
                    await MainActor.run { self?.progress = 0.5 + value * 0.5 }
                }
                func encode(smaller: Bool) async throws {
                    let worker = Task.detached(priority: .userInitiated) {
                        try await UgoiraEncoder.encode(frames: frames, destination: url, smaller: smaller, progress: update)
                    }
                    try await withTaskCancellationHandler {
                        try await worker.value
                    } onCancel: {
                        worker.cancel()
                    }
                }
                do { try await encode(smaller: false) } catch UgoiraExportError.unsupportedResolution {
                    try Task.checkCancellation()
                    needsSmaller = true
                    let accepted = await withCheckedContinuation { resolutionChoice = $0 }
                    needsSmaller = false
                    try Task.checkCancellation()
                    guard accepted else { throw CancellationError() }
                    try await encode(smaller: true)
                }
                try Task.checkCancellation()
                try await write(url)
                completion(true)
            } catch {
                failed = !Task.isCancelled && !(error is CancellationError)
                completion(false)
            }
            progress = nil
            needsSmaller = false
        }
        tail = task
        // Cancellation always targets the running job, including while queued jobs await it.
        Task { @MainActor in
            await previous?.value
            active = task
        }
    }

    public func chooseSmaller(_ value: Bool) {
        resolutionChoice?.resume(returning: value)
        resolutionChoice = nil
    }
    public func cancel() {
        active?.cancel()
        chooseSmaller(false)
    }
    public func dismissError() { failed = false }
}

private struct UgoiraExportStatus: ViewModifier {
    @State private var exporter = UgoiraExporter.shared
    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let progress = exporter.progress {
                VStack(spacing: 8) {
                    if exporter.needsSmaller {
                        Text("ugoira_resolution_unsupported", bundle: FlareAppleUILocalization.bundle)
                        Button {
                            exporter.chooseSmaller(true)
                        } label: {
                            Text("ugoira_export_smaller", bundle: FlareAppleUILocalization.bundle)
                        }
                    } else {
                        ProgressView(value: progress)
                        Text(verbatim: FlareAppleUILocalization.string("ugoira_export_progress", arguments: [Int(progress * 100)]))
                    }
                    Button("Cancel", role: .cancel) { exporter.cancel() }
                }.padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12)).padding().frame(maxWidth: 360)
            } else if exporter.failed {
                HStack {
                    Text("ugoira_export_failed", bundle: FlareAppleUILocalization.bundle)
                    Button("Dismiss") { exporter.dismissError() }
                }.padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12)).padding()
            }
        }
    }
}
extension View {
    public func ugoiraExportStatus() -> some View { modifier(UgoiraExportStatus()) }
}
