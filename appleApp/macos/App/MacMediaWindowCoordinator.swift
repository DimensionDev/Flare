import AppKit
import FlareAppleUI
import KotlinSharedUI
import SwiftUI

struct MacMediaWindowValue: Codable, Hashable {
    let id: UUID
}

struct MacMediaWindowRequest: Identifiable {
    let id: UUID
    let medias: [any UiMedia]
    let initialIndex: Int
    let preview: String?
    let shareContext: MacMediaShareContext?

    init(
        id: UUID = UUID(),
        medias: [any UiMedia],
        initialIndex: Int,
        preview: String?,
        shareContext: MacMediaShareContext? = nil
    ) {
        self.id = id
        self.medias = medias
        self.initialIndex = initialIndex
        self.preview = preview
        self.shareContext = shareContext
    }
}

@MainActor
final class MacMediaWindowCoordinator {
    static let shared = MacMediaWindowCoordinator()

    private var requests: [UUID: MacMediaWindowRequest] = [:]
    private var pendingPlacementAspectRatios: [CGFloat?] = []
    private var lastPlacementAspectRatio: CGFloat?

    private init() {}

    func request(for id: UUID?) -> MacMediaWindowRequest? {
        guard let id else {
            return nil
        }
        return requests[id]
    }

    func open(route: Route, openWindow: OpenWindowAction) {
        switch route {
        case .mediaImage(let url, let preview, let customHeaders):
            open(
                medias: [
                    UiMediaImage(
                        url: url,
                        previewUrl: preview ?? url,
                        description: nil,
                        height: 0,
                        width: 0,
                        sensitive: false,
                        customHeaders: customHeaders
                    )
                ],
                initialIndex: 0,
                preview: preview ?? url,
                openWindow: openWindow
            )
        case .mediaRaw(let medias, let initialIndex, let preview):
            open(
                medias: medias,
                initialIndex: initialIndex,
                preview: preview,
                openWindow: openWindow
            )
        default:
            break
        }
    }

    func open(
        post: UiTimelineV2.Post,
        media: any UiMedia,
        index: Int,
        openWindow: OpenWindowAction
    ) {
        let medias = Array(post.images)
        let selectedIndex: Int
        if medias.indices.contains(index) {
            selectedIndex = index
        } else {
            selectedIndex = medias.firstIndex { $0.url == media.url } ?? 0
        }
        open(
            medias: medias.isEmpty ? [media] : medias,
            initialIndex: selectedIndex,
            preview: media.mediaWindowPreview,
            shareContext: MacMediaShareContext(
                statusKey: post.statusKey.description(),
                userHandle: post.user?.handle.canonical
            ),
            openWindow: openWindow
        )
    }

    func open(
        medias: [any UiMedia],
        initialIndex: Int,
        preview: String?,
        shareContext: MacMediaShareContext? = nil,
        openWindow: OpenWindowAction
    ) {
        guard !medias.isEmpty else {
            return
        }
        let request = MacMediaWindowRequest(
            medias: medias,
            initialIndex: initialIndex,
            preview: preview,
            shareContext: shareContext
        )
        requests[request.id] = request
        pendingPlacementAspectRatios.append(
            Self.initialAspectRatio(
                medias: medias,
                initialIndex: initialIndex
            )
        )
        openWindow(
            id: MacWindowID.media,
            value: MacMediaWindowValue(id: request.id)
        )
    }

    func placementSize(maxWindowSize: CGSize) -> CGSize {
        if !pendingPlacementAspectRatios.isEmpty {
            lastPlacementAspectRatio = pendingPlacementAspectRatios.removeFirst() ?? nil
        }
        if let aspectRatio = lastPlacementAspectRatio?.validMediaWindowAspectRatio {
            return Self.windowSize(
                aspectRatio: aspectRatio,
                maxWindowSize: maxWindowSize
            )
        }
        return Self.fallbackWindowSize(maxWindowSize: maxWindowSize)
    }

    private static func initialAspectRatio(
        medias: [any UiMedia],
        initialIndex: Int
    ) -> CGFloat? {
        guard !medias.isEmpty else {
            return nil
        }
        let index = min(max(initialIndex, 0), medias.count - 1)
        return medias[index].aspectRatio?.validMediaWindowAspectRatio
    }

    private static func fallbackWindowSize(maxWindowSize: CGSize) -> CGSize {
        CGSize(
            width: min(960, maxWindowSize.width),
            height: min(720, maxWindowSize.height)
        )
    }

    private static func windowSize(
        aspectRatio: CGFloat,
        maxWindowSize: CGSize
    ) -> CGSize {
        if maxWindowSize.width / aspectRatio <= maxWindowSize.height {
            return CGSize(
                width: maxWindowSize.width,
                height: maxWindowSize.width / aspectRatio
            )
        }

        return CGSize(
            width: maxWindowSize.height * aspectRatio,
            height: maxWindowSize.height
        )
    }
}

struct MacMediaWindowRoot: View {
    @Environment(\.dismiss) private var dismiss
    @State private var request: MacMediaWindowRequest?

    init(value: MacMediaWindowValue?) {
        _request = .init(
            initialValue: MacMediaWindowCoordinator.shared.request(for: value?.id)
        )
    }

    var body: some View {
        if let request {
            NavigationStack {
                RawMediaScreen(
                    medias: request.medias,
                    initialIndex: request.initialIndex,
                    preview: request.preview,
                    shareContext: request.shareContext
                )
                .navigationBarBackButtonHidden()
            }
            .background(MediaWindowConfigurator())
            .onExitCommand {
                dismiss()
            }
            .id(request.id)
        } else {
            EmptyView()
                .onAppear {
                    dismiss()
                }
        }
    }
}

private extension UiMedia {
    var mediaWindowPreview: String? {
        switch onEnum(of: self) {
        case .image(let image):
            image.previewUrl
        case .video(let video):
            video.thumbnailUrl
        case .gif(let gif):
            gif.previewUrl
        case .audio:
            nil
        }
    }
}

private extension CGFloat {
    var validMediaWindowAspectRatio: CGFloat? {
        guard isFinite, self > 0 else {
            return nil
        }
        return Swift.min(Swift.max(self, 0.05), 20)
    }
}

private struct MediaWindowConfigurator: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        DispatchQueue.main.async {
            configure(window: view.window)
        }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        DispatchQueue.main.async {
            configure(window: nsView.window)
        }
    }

    private func configure(window: NSWindow?) {
        guard let window else {
            return
        }
        window.titlebarAppearsTransparent = true
        window.styleMask.insert(.fullSizeContentView)
        window.backgroundColor = .black
        window.isMovableByWindowBackground = true
    }
}
