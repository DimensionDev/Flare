import FlareAppleCore
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import SwiftUI
import UIKit

struct IOSStatusShareImageContent: View {
    let data: UiTimelineV2
    let colorScheme: ColorScheme
    let timelineAppearance: TimelineAppearance

    var body: some View {
        TimelineView(
            data: data,
            detailStatusKey: data.statusKey,
            showTranslate: false
        )
        .frame(width: 360)
        .padding()
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(.rect(cornerRadius: 16))
        .contentShape(RoundedRectangle(cornerRadius: 16))
        .shadow(radius: 8)
        .padding(64)
        .background(Color(.systemGroupedBackground))
        .environment(\.colorScheme, colorScheme)
        .environment(\.timelineAppearance, timelineAppearance.withSharePreviewDefaults())
    }
}

enum IOSStatusShareImageRenderer {
    @MainActor
    static func render(
        post: UiTimelineV2,
        colorScheme: ColorScheme,
        timelineAppearance: TimelineAppearance
    ) async -> UIImage? {
        await snapshot(
            view: IOSStatusShareImageContent(
                data: post,
                colorScheme: colorScheme,
                timelineAppearance: timelineAppearance
            ),
            colorScheme: colorScheme
        )
    }

    @MainActor
    private static func snapshot<Content: View>(
        view content: Content,
        colorScheme: ColorScheme,
        proposedWidth: CGFloat = 520,
        delay: TimeInterval = 0.1
    ) async -> UIImage? {
        guard proposedWidth.isFinite, proposedWidth > 0 else {
            return nil
        }

        let controller = UIHostingController(rootView: content.edgesIgnoringSafeArea(.top))
        controller.overrideUserInterfaceStyle = colorScheme == .dark ? .dark : .light
        controller.sizingOptions = [.intrinsicContentSize]
        guard let view = controller.view else {
            return nil
        }
        view.backgroundColor = .systemGroupedBackground

        guard let initialSize = controller.snapshotSize(proposedWidth: proposedWidth) else {
            return nil
        }
        view.prepareForSnapshot(size: initialSize)
        try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))

        guard let targetSize = controller.snapshotSize(proposedWidth: proposedWidth) else {
            return nil
        }
        view.prepareForSnapshot(size: targetSize)

        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = true
        guard targetSize.isValidSnapshotSize(scale: format.scale) else {
            return nil
        }
        let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
        return renderer.image { context in
            if !view.drawHierarchy(in: view.bounds, afterScreenUpdates: true) {
                view.layer.render(in: context.cgContext)
            }
        }
    }
}

final class IOSReferenceShareImageRenderer: ReferenceShareImageRenderer {
    private let colorScheme: ColorScheme
    private let timelineAppearance: TimelineAppearance

    init(colorScheme: ColorScheme, timelineAppearance: TimelineAppearance) {
        self.colorScheme = colorScheme
        self.timelineAppearance = timelineAppearance
    }

    nonisolated func render(
        post: UiTimelineV2,
        completion: @escaping (ComposeData.Media?, String?) -> Void
    ) {
        let request = IOSReferenceShareRenderRequest(post: post, completion: completion)
        Task { @MainActor in
            guard let image = await IOSStatusShareImageRenderer.render(
                post: request.post,
                colorScheme: colorScheme,
                timelineAppearance: timelineAppearance
            ), let data = image.pngData() else {
                request.completion(nil, "Unable to render referenced post image.")
                return
            }
            let media = ComposeData.Media(
                file: FileItem(
                    name: "reference-\(UUID().uuidString).png",
                    data: KotlinByteArray.from(data: data),
                    type: .image,
                    mimeType: "image/png"
                ),
                altText: nil
            )
            request.completion(media, nil)
        }
    }
}

private nonisolated final class IOSReferenceShareRenderRequest: @unchecked Sendable {
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

@MainActor
private extension UIHostingController {
    func snapshotSize(proposedWidth: CGFloat) -> CGSize? {
        let measuredSize = sizeThatFits(
            in: CGSize(
                width: proposedWidth,
                height: UIView.layoutFittingExpandedSize.height
            )
        )
        let size = CGSize(width: ceil(measuredSize.width), height: ceil(measuredSize.height))
        return size.width.isFinite && size.height.isFinite && size.width > 0 && size.height > 0
            ? size
            : nil
    }
}

@MainActor
private extension UIView {
    func prepareForSnapshot(size: CGSize) {
        bounds = CGRect(origin: .zero, size: size)
        setNeedsLayout()
        layoutIfNeeded()
    }
}

private extension CGSize {
    func isValidSnapshotSize(scale: CGFloat) -> Bool {
        let pixelWidth = width * scale
        let pixelHeight = height * scale
        let pixelCount = pixelWidth * pixelHeight
        return width.isFinite &&
            height.isFinite &&
            width > 0 &&
            height > 0 &&
            pixelWidth <= 16_384 &&
            pixelHeight <= 16_384 &&
            pixelCount <= 64_000_000
    }
}
