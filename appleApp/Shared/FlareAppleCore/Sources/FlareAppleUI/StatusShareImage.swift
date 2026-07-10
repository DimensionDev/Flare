#if os(macOS)
import AppKit
import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI

private let macStatusSharePreviewTimelineWidth: CGFloat = 360
private let macStatusShareScreenshotScale: CGFloat = 2
private let macStatusSharePreviewCornerRadius: CGFloat = 16

public struct MacStatusShareImageContent: View {
    private let data: UiTimelineV2
    private let colorScheme: ColorScheme
    private let timelineAppearance: TimelineAppearance

    public init(
        data: UiTimelineV2,
        colorScheme: ColorScheme,
        timelineAppearance: TimelineAppearance
    ) {
        self.data = data
        self.colorScheme = colorScheme
        self.timelineAppearance = timelineAppearance
    }

    public var body: some View {
        TimelineView(
            data: data,
            detailStatusKey: data.statusKey,
            showTranslate: false
        )
        .frame(width: macStatusSharePreviewTimelineWidth, alignment: .leading)
        .padding()
        .background(Color.flareSecondarySystemGroupedBackground)
        .clipShape(RoundedRectangle(cornerRadius: macStatusSharePreviewCornerRadius))
        .contentShape(RoundedRectangle(cornerRadius: macStatusSharePreviewCornerRadius))
        .shadow(radius: 8)
        .padding(64)
        .background(Color.flareSystemGroupedBackground)
        .environment(\.colorScheme, colorScheme)
        .environment(\.timelineAppearance, timelineAppearance.withSharePreviewDefaults())
    }
}

public enum MacStatusShareImageRenderer {
    @MainActor
    public static func render<Content: View>(
        view: Content,
        fileName: String
    ) async throws -> URL {
        let hostingView = NSHostingView(rootView: view.fixedSize(horizontal: false, vertical: true))
        let fittingSize = hostingView.fittingSize
        guard fittingSize.width > 0, fittingSize.height > 0 else {
            throw MacStatusShareImageError.invalidSnapshotSize
        }

        hostingView.frame = NSRect(origin: .zero, size: fittingSize)
        hostingView.wantsLayer = true
        hostingView.layer?.contentsScale = macStatusShareScreenshotScale
        hostingView.layoutSubtreeIfNeeded()
        try? await Task.sleep(nanoseconds: 100_000_000)
        hostingView.layoutSubtreeIfNeeded()

        let pixelWidth = max(1, Int((fittingSize.width * macStatusShareScreenshotScale).rounded(.up)))
        let pixelHeight = max(1, Int((fittingSize.height * macStatusShareScreenshotScale).rounded(.up)))
        guard let representation = NSBitmapImageRep(
            bitmapDataPlanes: nil,
            pixelsWide: pixelWidth,
            pixelsHigh: pixelHeight,
            bitsPerSample: 8,
            samplesPerPixel: 4,
            hasAlpha: true,
            isPlanar: false,
            colorSpaceName: .deviceRGB,
            bytesPerRow: 0,
            bitsPerPixel: 0
        ) else {
            throw MacStatusShareImageError.snapshotFailed
        }
        representation.size = fittingSize
        hostingView.cacheDisplay(in: hostingView.bounds, to: representation)

        guard let pngData = representation.representation(using: .png, properties: [:]) else {
            throw MacStatusShareImageError.pngEncodingFailed
        }

        let directoryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("flare-status-share-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        let fileURL = directoryURL.appendingPathComponent(
            MediaFileNamePolicy.shared.safeLocalFileName(value: fileName, fallback: "flare-status.png")
        )
        try pngData.write(to: fileURL, options: .atomic)
        return fileURL
    }
}

public final class MacReferenceShareImageRenderer: ReferenceShareImageRenderer {
    private let colorScheme: ColorScheme
    private let timelineAppearance: TimelineAppearance

    public init(colorScheme: ColorScheme, timelineAppearance: TimelineAppearance) {
        self.colorScheme = colorScheme
        self.timelineAppearance = timelineAppearance
    }

    public nonisolated func render(
        post: UiTimelineV2,
        completion: @escaping (ComposeData.Media?, String?) -> Void
    ) {
        let request = MacReferenceShareRenderRequest(post: post, completion: completion)
        Task { @MainActor in
            do {
                let fileURL = try await MacStatusShareImageRenderer.render(
                    view: MacStatusShareImageContent(
                        data: request.post,
                        colorScheme: colorScheme,
                        timelineAppearance: timelineAppearance
                    ),
                    fileName: "flare-reference-\(UUID().uuidString).png"
                )
                let data = try Data(contentsOf: fileURL)
                request.completion(
                    ComposeData.Media(
                        file: FileItem(
                            name: fileURL.lastPathComponent,
                            data: KotlinByteArray.from(data: data),
                            type: .image,
                            mimeType: "image/png"
                        ),
                        altText: nil
                    ),
                    nil
                )
            } catch {
                request.completion(nil, error.localizedDescription)
            }
        }
    }
}

private nonisolated final class MacReferenceShareRenderRequest: @unchecked Sendable {
    let post: UiTimelineV2
    let completion: (ComposeData.Media?, String?) -> Void

    init(
        post: UiTimelineV2,
        completion: @escaping (ComposeData.Media?, String?) -> Void
    ) {
        self.post = post
        self.completion = completion
    }
}

private enum MacStatusShareImageError: LocalizedError {
    case invalidSnapshotSize
    case snapshotFailed
    case pngEncodingFailed

    var errorDescription: String? {
        switch self {
        case .invalidSnapshotSize:
            String(localized: "share_screenshot_invalid_size")
        case .snapshotFailed:
            String(localized: "share_screenshot_failed")
        case .pngEncodingFailed:
            String(localized: "share_screenshot_encoding_failed")
        }
    }
}
#endif
