import SwiftUI
import FlareAppleUI
import KotlinSharedUI
import LazyPager
import AVKit
import SwiftUIBackports
import UIKit
import FlareAppleCore

struct MediaViewerShareContext {
    let statusKey: String?
    let userHandle: String?
}

struct MediaViewerOverlayContext {
    let selectedMedia: (any UiMedia)?
    let mediaCount: Int
    let selectedIndex: Binding<Int>
    let showData: Bool
    let isLandscapeViewing: Bool
    let isPlaying: Binding<Bool>
    let currentTime: Binding<CMTime>
    let videoState: VideoState
    let playbackRate: Float
}

struct MediaViewerScreen<SupplementaryOverlay: View>: View {
    @Environment(\.dismiss) private var dismiss

    let medias: [any UiMedia]
    let initialIndex: Int
    let preview: String?
    let shareContext: MediaViewerShareContext?
    let showsSupplementaryOverlay: Bool
    @ViewBuilder let supplementaryOverlay: (MediaViewerOverlayContext) -> SupplementaryOverlay

    @State private var selectedIndex: Int
    @State private var isPlaying: Bool = true
    @State private var videoState: VideoState = .idle
    @State private var currentTime: CMTime = .zero
    @State private var opacity: CGFloat = 1
    @State private var dismissOffset: CGFloat = 0
    @State private var isDismissing = false
    @State private var showData = true
    @State private var protectInitialPagerSelection: Bool
    @State private var didApplyInitialSelection = false
    @State private var isPreparingShare = false
    @State private var holdsPlaybackSession = false
    @State private var playbackRate: Float = 1
    @State private var isLandscapeViewing = false

    init(
        medias: [any UiMedia],
        initialIndex: Int,
        preview: String?,
        shareContext: MediaViewerShareContext? = nil,
        showsSupplementaryOverlay: Bool = false,
        @ViewBuilder supplementaryOverlay: @escaping (MediaViewerOverlayContext) -> SupplementaryOverlay
    ) {
        self.medias = medias
        self.initialIndex = initialIndex
        self.preview = preview
        self.shareContext = shareContext
        self.showsSupplementaryOverlay = showsSupplementaryOverlay
        self.supplementaryOverlay = supplementaryOverlay
        self._selectedIndex = .init(initialValue: max(0, initialIndex))
        self._protectInitialPagerSelection = .init(initialValue: initialIndex > 0)
    }

    var body: some View {
        ZStack {
            Group {
                if medias.isEmpty {
                    Group {
                        if let preview {
                            AdaptiveKFImage(data: preview, placeholder: nil)
                        } else {
                            ProgressView()
                        }
                    }
                } else {
                    // LazyPager 1.2.1 only supports downward dismissal.
                    ZStack(alignment: .bottom) {
                        LazyPager(data: medias, page: pagerSelectedIndex) { media in
                            mediaContent(media)
                                .contextMenu {
                                    MediaViewerContextMenu(
                                        media: media,
                                        showsDownloadAll: medias.count > 1,
                                        isPreparingShare: isPreparingShare,
                                        onDownload: {
                                            saveMedia(media)
                                        },
                                        onDownloadAll: {
                                            saveAllMedia()
                                        },
                                        onShareImage: {
                                            shareSelectedImage(media)
                                        },
                                        onCopyLink: {
                                            UIPasteboard.general.string = media.url
                                        }
                                    )
                                }
                        }
                        .onTap {
                            withAnimation {
                                showData.toggle()
                            }
                        }
                        .onDoubleTap {}
                        .onDrag {
                            protectInitialPagerSelection = false
                        }
                        .zoomable { item in
                            if item.isVideoMedia {
                                return .disabled
                            } else {
                                return .custom(min: 1, max: 5, doubleTap: .scale(2))
                            }
                        }
                        .settings { config in
                            config.preloadAmount = 99
                        }
                        .offset(
                            y: MediaViewerDismissGesturePolicy.verticalOffset(
                                for: .media,
                                translationY: dismissOffset
                            )
                        )

                        if shouldShowBottomOverlay {
                            bottomOverlay
                                .offset(
                                    y: MediaViewerDismissGesturePolicy.verticalOffset(
                                        for: .bottomOverlay,
                                        translationY: dismissOffset
                                    )
                                )
                        }

                        MediaViewerDismissGestureHost(
                            onChanged: updateDismissGesture,
                            onEnded: endDismissGesture,
                            onCancelled: cancelDismissGesture
                        )
                    }
                }
            }
            .ignoresSafeArea()

            if showData {
                topOverlay
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .opacity(opacity)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .allowsHitTesting(!isDismissing)
                    .zIndex(3)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            applyInitialSelectionIfNeeded()
        }
        .onChange(of: mediaSignature) { _, _ in
            applyInitialSelectionIfNeeded()
        }
        .onChange(of: selectedIndex) { _, _ in
            isPlaying = true
            videoState = .idle
            currentTime = .zero
            playbackRate = 1
        }
        .onChange(of: isVideoActivelyPlaying) { _, newValue in
            updatePlaybackSession(playing: newValue)
        }
        .onChange(of: isLandscapeViewing) { _, newValue in
            MediaOrientationController.setLandscape(newValue)
        }
        .onDisappear {
            if holdsPlaybackSession {
                AudioSessionManager.shared.endPlayback()
                holdsPlaybackSession = false
            }
            if isLandscapeViewing {
                MediaOrientationController.setLandscape(false)
            }
        }
        .background(.black.opacity(opacity))
        .background(ClearFullScreenBackground())
        .toolbar(.hidden, for: .navigationBar)
    }

    @ViewBuilder
    private func mediaContent(_ media: any UiMedia) -> some View {
        switch onEnum(of: media) {
        case .image(let image):
            AdaptiveKFImage(data: image.url, placeholder: image.previewUrl, customHeader: image.customHeaders)
        case .video(let video):
            if medias.indices.contains(selectedIndex), medias[selectedIndex].url == video.url {
                StatusMediaVideoView(
                    data: video,
                    play: $isPlaying,
                    videoState: $videoState,
                    time: $currentTime,
                    playbackRate: $playbackRate
                )
            } else {
                NetworkImage(data: video.thumbnailUrl, customHeader: video.customHeaders)
                    .scaledToFit()
            }
        case .gif(let gif):
            NetworkImage(data: gif.url, placeholder: gif.previewUrl, customHeader: gif.customHeaders)
                .scaledToFit()
        case .audio:
            EmptyView()
        }
    }

    private var topOverlay: some View {
        HStack(spacing: 8) {
            topOverlayButton(width: 44, action: { dismiss() }) {
                Image(fontAwesome: .xmark)
            }
            .accessibilityLabel("Close")
            .backport
            .glassEffect(.regularInteractive, in: .capsule, fallbackBackground: .regularMaterial)

            Spacer()

            if !medias.isEmpty {
                HStack(spacing: 0) {
                    topOverlayButton {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            isLandscapeViewing.toggle()
                        }
                    } label: {
                        Image(systemName: isLandscapeViewing ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                    }
                    .accessibilityLabel(Text(verbatim: isLandscapeViewing ? "Exit landscape view" : "Landscape view"))

                    if let selectedMedia, case .image = onEnum(of: selectedMedia) {
                        topOverlayButton {
                            saveMedia(selectedMedia)
                        } label: {
                            Image(fontAwesome: .download)
                        }

                        topOverlayButton {
                            shareSelectedImage(selectedMedia)
                        } label: {
                            Image(fontAwesome: .shareNodes)
                        }
                        .disabled(isPreparingShare)
                        .accessibilityLabel("Share image")
                    } else if let selectedMedia, case .video = onEnum(of: selectedMedia) {
                        topOverlayButton {
                            saveMedia(selectedMedia)
                        } label: {
                            Image(fontAwesome: .download)
                        }
                    }
                }
                .padding(.horizontal, 4)
                .backport
                .glassEffect(.regularInteractive, in: .capsule, fallbackBackground: .regularMaterial)
            }
        }
        .buttonStyle(.plain)
        .foregroundStyle(.white)
        .padding(.horizontal)
    }

    private func topOverlayButton<Label: View>(
        width: CGFloat = 48,
        action: @escaping () -> Void,
        @ViewBuilder label: () -> Label
    ) -> some View {
        Button(action: action, label: label)
            .frame(width: width, height: 44)
            .font(.body)
            .imageScale(.large)
            .contentShape(Rectangle())
    }

    @ViewBuilder
    private var bottomOverlay: some View {
        if #available(iOS 26.0, *) {
            bottomOverlayContent
                .padding()
                .backport
                .glassEffect(.tinted(.init(.systemGroupedBackground).opacity(0.5)), in: .rect(corners: .concentric, isUniform: true), fallbackBackground: .regularMaterial)
                .padding()
                .transition(.move(edge: .bottom).combined(with: .opacity))
        } else {
            bottomOverlayContent
                .padding()
                .safeAreaPadding(.bottom)
                .backport
                .glassEffect(.regular, in: .rect(cornerRadius: 24, style: .continuous), fallbackBackground: .regularMaterial)
                .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }

    private var bottomOverlayContent: some View {
        VStack(spacing: 8) {
            if showData, !isLandscapeViewing, medias.count > 1 {
                if !showsSupplementaryOverlay, medias.count > 10 {
                    MediaPageSlider(count: medias.count, page: $selectedIndex)
                } else {
                    LazyPagerIndicator(count: medias.count, page: $selectedIndex)
                }
            }

            if let selectedMedia, case .video = onEnum(of: selectedMedia) {
                VideoControlView(
                    isPlaying: $isPlaying,
                    currentTime: $currentTime,
                    videoState: videoState,
                    playbackRate: playbackRate
                )
            }

            if showData, !isLandscapeViewing, showsSupplementaryOverlay {
                supplementaryOverlay(overlayContext)
            }
        }
    }

    private var overlayContext: MediaViewerOverlayContext {
        MediaViewerOverlayContext(
            selectedMedia: selectedMedia,
            mediaCount: medias.count,
            selectedIndex: $selectedIndex,
            showData: showData,
            isLandscapeViewing: isLandscapeViewing,
            isPlaying: $isPlaying,
            currentTime: $currentTime,
            videoState: videoState,
            playbackRate: playbackRate
        )
    }

    private var pagerSelectedIndex: Binding<Int> {
        Binding(
            get: {
                selectedIndex
            },
            set: { newValue in
                let nextIndex = clampedIndex(newValue, count: medias.count)
                if protectInitialPagerSelection,
                   selectedIndex > 0,
                   nextIndex < selectedIndex {
                    return
                }
                protectInitialPagerSelection = false
                selectedIndex = nextIndex
            }
        )
    }

    private var selectedMedia: (any UiMedia)? {
        guard medias.indices.contains(selectedIndex) else {
            return nil
        }
        return medias[selectedIndex]
    }

    private var isVideoActivelyPlaying: Bool {
        if case .playing = videoState { return true }
        return false
    }

    private var selectedMediaIsVideo: Bool {
        selectedMedia?.isVideoMedia == true
    }

    private var shouldShowBottomOverlay: Bool {
        if selectedMediaIsVideo {
            return showData
        }
        return showData && !isLandscapeViewing && (medias.count > 1 || showsSupplementaryOverlay)
    }

    private var mediaSignature: String {
        medias.map { $0.url }.joined(separator: "\n")
    }

    private func applyInitialSelectionIfNeeded() {
        guard !medias.isEmpty else {
            return
        }
        if !didApplyInitialSelection || selectedIndex >= medias.count {
            let initialSelection = clampedIndex(initialIndex, count: medias.count)
            selectedIndex = initialSelection
            protectInitialPagerSelection = initialSelection > 0
            didApplyInitialSelection = true
        }
    }

    private func updatePlaybackSession(playing: Bool) {
        if playing, !holdsPlaybackSession {
            AudioSessionManager.shared.beginPlayback()
            holdsPlaybackSession = true
        } else if !playing, holdsPlaybackSession {
            AudioSessionManager.shared.endPlayback()
            holdsPlaybackSession = false
        }
    }

    private func clampedIndex(_ index: Int, count: Int) -> Int {
        guard count > 0 else {
            return 0
        }
        return min(max(index, 0), count - 1)
    }

    private func updateDismissGesture(translationY: CGFloat, containerHeight: CGFloat) {
        guard !isDismissing else { return }
        dismissOffset = translationY
        opacity = 1 - MediaViewerDismissGesturePolicy.progress(
            translationY: translationY,
            containerHeight: containerHeight
        )
    }

    private func endDismissGesture(
        translationY: CGFloat,
        velocityY: CGFloat,
        containerHeight: CGFloat
    ) -> Bool {
        guard !isDismissing else { return true }
        guard MediaViewerDismissGesturePolicy.shouldDismiss(
            translationY: translationY,
            velocityY: velocityY,
            containerHeight: containerHeight
        ) else {
            cancelDismissGesture()
            return false
        }

        isDismissing = true
        let direction: CGFloat = translationY == 0
            ? (velocityY < 0 ? -1 : 1)
            : (translationY < 0 ? -1 : 1)
        withAnimation(.linear(duration: 0.2)) {
            dismissOffset = direction * containerHeight
            opacity = 0
        } completion: {
            if MediaViewerDismissGesturePolicy.disablesSystemAnimationOnDismiss {
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    dismiss()
                }
            } else {
                dismiss()
            }
        }
        return true
    }

    private func cancelDismissGesture() {
        guard !isDismissing else { return }
        withAnimation(.spring(response: 0.25, dampingFraction: 0.9)) {
            dismissOffset = 0
            opacity = 1
        }
    }

    private func saveMedia(
        _ media: any UiMedia,
        showsDownloadStarted: Bool = true,
        showsSaveResult: Bool = true,
        completion: (@Sendable (Bool) -> Void)? = nil
    ) {
        switch onEnum(of: media) {
        case .image(let image):
            MediaSaver.shared.saveImage(
                url: image.url,
                customHeaders: image.customHeaders,
                showsDownloadStarted: showsDownloadStarted,
                showsSaveResult: showsSaveResult,
                completion: completion
            )
        case .gif(let gif):
            MediaSaver.shared.saveImage(
                url: gif.url,
                customHeaders: gif.customHeaders,
                showsDownloadStarted: showsDownloadStarted,
                showsSaveResult: showsSaveResult,
                completion: completion
            )
        case .video(let video):
            MediaSaver.shared.saveVideo(
                url: video.url,
                customHeaders: video.customHeaders,
                showsDownloadStarted: showsDownloadStarted,
                showsSaveResult: showsSaveResult,
                completion: completion
            )
        case .audio(let audio):
            MediaSaver.shared.saveFile(
                url: audio.url,
                fileName: fileName(for: media),
                customHeaders: audio.customHeaders,
                showsDownloadStarted: showsDownloadStarted,
                showsSaveResult: showsSaveResult,
                completion: completion
            )
        }
    }

    private func saveAllMedia() {
        guard !medias.isEmpty else {
            return
        }

        MediaSaver.shared.showDownloadStarted()
        let tracker = MediaViewerBatchSaveTracker(count: medias.count)

        let onComplete: @Sendable (Bool) -> Void = { success in
            Task {
                if let batchSuccess = await tracker.complete(success: success) {
                    await MainActor.run {
                        MediaSaver.shared.showBatchSaveResult(success: batchSuccess)
                    }
                }
            }
        }

        for media in medias {
            saveMedia(
                media,
                showsDownloadStarted: false,
                showsSaveResult: false,
                completion: onComplete
            )
        }
    }

    private func fileName(for media: any UiMedia) -> String {
        if let statusKey = shareContext?.statusKey {
            let mediaIndex = medias.firstIndex { $0.url == media.url } ?? 0
            return MediaFileNamePolicy.shared.statusMediaFileName(
                statusKey: statusKey,
                userHandle: shareContext?.userHandle ?? "unknown",
                media: media,
                mediaIndex: Int32(mediaIndex)
            )
        }
        return MediaFileNamePolicy.shared.rawMediaFileName(media: media)
    }

    private func shareSelectedImage(_ media: any UiMedia) {
        guard !isPreparingShare,
              case .image(let image) = onEnum(of: media) else {
            return
        }

        let sourceURL = image.url
        let customHeaders = image.customHeaders
        isPreparingShare = true
        Task {
            defer {
                isPreparingShare = false
            }
            do {
                let fileURL = try await OriginalImageShareFile.make(
                    url: sourceURL,
                    customHeaders: customHeaders,
                    statusKey: shareContext?.statusKey,
                    userHandle: shareContext?.userHandle,
                    onPreparingNeeded: {
                        MediaSaver.showPreparingMedia()
                    }
                )
                guard !Task.isCancelled,
                      selectedMedia?.url == sourceURL,
                      let presenter = topViewController() else {
                    return
                }
                let controller = UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
                controller.popoverPresentationController?.sourceView = presenter.view
                controller.popoverPresentationController?.sourceRect = presenter.view.bounds
                presenter.present(controller, animated: true)
            } catch {
                return
            }
        }
    }

    private func topViewController() -> UIViewController? {
        let rootViewController = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }?
            .windows
            .first { $0.isKeyWindow }?
            .rootViewController
        return topViewController(from: rootViewController)
    }

    private func topViewController(from viewController: UIViewController?) -> UIViewController? {
        if let navigationController = viewController as? UINavigationController {
            return topViewController(from: navigationController.visibleViewController)
        }
        if let tabBarController = viewController as? UITabBarController {
            return topViewController(from: tabBarController.selectedViewController)
        }
        if let presentedViewController = viewController?.presentedViewController {
            return topViewController(from: presentedViewController)
        }
        return viewController
    }
}

private struct MediaViewerDismissGestureHost: UIViewRepresentable {
    let onChanged: (CGFloat, CGFloat) -> Void
    let onEnded: (CGFloat, CGFloat, CGFloat) -> Bool
    let onCancelled: () -> Void

    func makeUIView(context: Context) -> MediaViewerDismissGestureHostView {
        let view = MediaViewerDismissGestureHostView()
        view.backgroundColor = .clear
        view.onWindowChanged = { [weak coordinator = context.coordinator, weak view] in
            coordinator?.installGesture(from: view)
        }
        return view
    }

    func updateUIView(_ uiView: MediaViewerDismissGestureHostView, context: Context) {
        context.coordinator.onChanged = onChanged
        context.coordinator.onEnded = onEnded
        context.coordinator.onCancelled = onCancelled
        DispatchQueue.main.async {
            context.coordinator.installGesture(from: uiView)
        }
    }

    static func dismantleUIView(
        _ uiView: MediaViewerDismissGestureHostView,
        coordinator: Coordinator
    ) {
        coordinator.uninstallGesture()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(
            onChanged: onChanged,
            onEnded: onEnded,
            onCancelled: onCancelled
        )
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onChanged: (CGFloat, CGFloat) -> Void
        var onEnded: (CGFloat, CGFloat, CGFloat) -> Bool
        var onCancelled: () -> Void

        private weak var sourceView: UIView?
        private weak var installedWindow: UIWindow?
        private weak var touchedZoomScrollView: UIScrollView?
        private var touchedZoomScrollViewInitialOffset: CGPoint?
        private var panRecognizer: UIPanGestureRecognizer?

        init(
            onChanged: @escaping (CGFloat, CGFloat) -> Void,
            onEnded: @escaping (CGFloat, CGFloat, CGFloat) -> Bool,
            onCancelled: @escaping () -> Void
        ) {
            self.onChanged = onChanged
            self.onEnded = onEnded
            self.onCancelled = onCancelled
        }

        func installGesture(from view: UIView?) {
            sourceView = view
            guard let window = view?.window, installedWindow !== window else { return }
            uninstallGesture()
            sourceView = view

            let recognizer = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
            recognizer.maximumNumberOfTouches = 1
            recognizer.cancelsTouchesInView = false
            recognizer.delegate = self
            window.addGestureRecognizer(recognizer)

            installedWindow = window
            panRecognizer = recognizer
        }

        func uninstallGesture() {
            if let panRecognizer {
                installedWindow?.removeGestureRecognizer(panRecognizer)
            }
            installedWindow = nil
            touchedZoomScrollView = nil
            touchedZoomScrollViewInitialOffset = nil
            panRecognizer = nil
        }

        @objc private func handlePan(_ recognizer: UIPanGestureRecognizer) {
            guard let sourceView else { return }
            let translationY = recognizer.translation(in: sourceView).y
            let containerHeight = sourceView.bounds.height

            switch recognizer.state {
            case .began, .changed:
                onChanged(translationY, containerHeight)
            case .ended:
                let didDismiss = onEnded(
                    translationY,
                    recognizer.velocity(in: sourceView).y,
                    containerHeight
                )
                finishTrackingTouchedScrollView(didDismiss: didDismiss)
            case .cancelled, .failed:
                onCancelled()
                finishTrackingTouchedScrollView(didDismiss: false)
            default:
                break
            }
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard gestureRecognizer === panRecognizer,
                  let panRecognizer,
                  let sourceView else { return false }
            let velocity = panRecognizer.velocity(in: sourceView)
            return MediaViewerDismissGesturePolicy.shouldBegin(
                velocityX: velocity.x,
                velocityY: velocity.y,
                zoomScale: touchedZoomScrollView?.zoomScale,
                minimumZoomScale: touchedZoomScrollView?.minimumZoomScale
            )
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            guard let sourceView,
                  let window = installedWindow else { return false }
            let point = touch.location(in: window)
            guard sourceView.convert(sourceView.bounds, to: window).contains(point) else {
                return false
            }
            let touchedScrollViews = ancestorScrollViews(from: touch.view)
            touchedZoomScrollView = touchedScrollViews.first {
                $0.maximumZoomScale > $0.minimumZoomScale
            }
            touchedZoomScrollViewInitialOffset = touchedZoomScrollView?.contentOffset
            return true
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            guard gestureRecognizer === panRecognizer else { return false }
            return MediaViewerDismissGesturePolicy.shouldTakePriority(
                over: otherGestureRecognizer
            )
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        private func ancestorScrollViews(from view: UIView?) -> [UIScrollView] {
            var scrollViews: [UIScrollView] = []
            var candidate = view
            while let current = candidate, current !== installedWindow {
                if let scrollView = current as? UIScrollView {
                    scrollViews.append(scrollView)
                }
                candidate = current.superview
            }
            return scrollViews
        }

        private func finishTrackingTouchedScrollView(didDismiss: Bool) {
            if MediaViewerDismissGesturePolicy.shouldRestoreTouchedScrollPosition(
                didDismiss: didDismiss
            ),
               let touchedZoomScrollView,
               let touchedZoomScrollViewInitialOffset {
                DispatchQueue.main.async { [weak touchedZoomScrollView] in
                    touchedZoomScrollView?.setContentOffset(
                        touchedZoomScrollViewInitialOffset,
                        animated: false
                    )
                }
            }
            touchedZoomScrollView = nil
            touchedZoomScrollViewInitialOffset = nil
        }
    }
}

private final class MediaViewerDismissGestureHostView: UIView {
    var onWindowChanged: (() -> Void)?

    override func didMoveToWindow() {
        super.didMoveToWindow()
        onWindowChanged?()
    }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        false
    }
}

private struct MediaViewerContextMenu: View {
    let media: any UiMedia
    let showsDownloadAll: Bool
    let isPreparingShare: Bool
    let onDownload: () -> Void
    let onDownloadAll: () -> Void
    let onShareImage: () -> Void
    let onCopyLink: () -> Void

    var body: some View {
        Button(action: onDownload) {
            Label {
                Text("media_menu_download", bundle: .main)
            } icon: {
                Image(fontAwesome: .download)
            }
        }

        if showsDownloadAll {
            Button(action: onDownloadAll) {
                Label {
                    Text("media_menu_download_all", bundle: .main)
                } icon: {
                    Image(fontAwesome: .download)
                }
            }
        }

        if case .image = onEnum(of: media) {
            Button(action: onShareImage) {
                Label {
                    Text("media_menu_share_image", bundle: .main)
                } icon: {
                    Image(fontAwesome: .shareNodes)
                }
            }
            .disabled(isPreparingShare)
        }

        Button(action: onCopyLink) {
            Label {
                Text("media_menu_copy_link", bundle: .main)
            } icon: {
                Image(systemName: "doc.on.doc")
            }
        }
    }
}

private actor MediaViewerBatchSaveTracker {
    private var remainingCount: Int
    private var hasFailure = false

    init(count: Int) {
        remainingCount = count
    }

    func complete(success: Bool) -> Bool? {
        hasFailure = hasFailure || !success
        remainingCount -= 1
        guard remainingCount == 0 else {
            return nil
        }
        return !hasFailure
    }
}

private struct MediaPageSlider: View {
    let count: Int
    @Binding var page: Int

    var body: some View {
        let maxPage = max(count - 1, 0)
        let displayedPage = clampedPage(page, maxPage: maxPage)

        HStack(spacing: 10) {
            Text(verbatim: "\(displayedPage + 1)")
                .frame(minWidth: 24, alignment: .trailing)

            Slider(
                value: Binding(
                    get: {
                        Double(displayedPage)
                    },
                    set: { newValue in
                        page = clampedPage(Int(newValue.rounded()), maxPage: maxPage)
                    }
                ),
                in: 0...Double(maxPage),
                step: 1
            )

            Text(verbatim: "\(count)")
                .frame(minWidth: 24, alignment: .leading)
        }
        .font(.caption.weight(.semibold))
        .monospacedDigit()
        .frame(minWidth: 220, maxWidth: 360)
    }

    private func clampedPage(_ value: Int, maxPage: Int) -> Int {
        min(max(value, 0), maxPage)
    }
}

extension MediaViewerScreen where SupplementaryOverlay == EmptyView {
    init(
        medias: [any UiMedia],
        initialIndex: Int,
        preview: String?,
        shareContext: MediaViewerShareContext? = nil
    ) {
        self.init(
            medias: medias,
            initialIndex: initialIndex,
            preview: preview,
            shareContext: shareContext,
            showsSupplementaryOverlay: false
        ) { _ in
            EmptyView()
        }
    }
}
