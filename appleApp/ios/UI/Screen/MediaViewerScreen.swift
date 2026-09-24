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

struct MediaViewerScreen: View {
    @Environment(\.globalAppearance.showPostInMediaViewer) private var showPostInMediaViewer
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss

    let medias: [any UiMedia]
    let initialIndex: Int
    let preview: String?
    let previewAspectRatio: CGFloat?
    let previewIsImage: Bool
    let shareContext: MediaViewerShareContext?
    let post: UiTimelineV2.Post?
    let quotes: [UiTimelineV2.Post]

    @State private var selectedIndex: Int
    @State private var isPlaying: Bool = true
    @State private var videoState: VideoState = .idle
    @State private var playbackTimes: [String: CMTime] = [:]
    @State private var opacity: CGFloat = 1
    @State private var dismissOffset: CGFloat = 0
    @State private var isDismissing = false
    @State private var showData = true
    @State private var protectInitialPagerSelection: Bool
    @State private var didApplyInitialSelection = false
    @State private var isPreparingShare = false
    @State private var playbackRate: Float = 1
    @State private var isLandscapeViewing = false
    @State private var postSheetPresented = false
    @State private var postSummaryHeight: CGFloat = 120
    @State private var postDetent: PresentationDetent = .height(120)

    init(
        medias: [any UiMedia],
        initialIndex: Int,
        preview: String?,
        previewAspectRatio: CGFloat? = nil,
        previewIsImage: Bool = true,
        shareContext: MediaViewerShareContext? = nil,
        post: UiTimelineV2.Post? = nil,
        quotes: [UiTimelineV2.Post] = []
    ) {
        self.medias = medias
        self.initialIndex = initialIndex
        self.preview = preview
        self.previewAspectRatio = previewAspectRatio
        self.previewIsImage = previewIsImage
        self.shareContext = shareContext
        self.post = post
        self.quotes = quotes
        self.selectedIndex = max(0, initialIndex)
        self.protectInitialPagerSelection = initialIndex > 0
    }

    var body: some View {
        ZStack {
            Group {
                if medias.isEmpty {
                    Group {
                        if let preview {
                            LazyPager(data: [preview]) { preview in
                                switch MediaViewerImageLayoutPolicy.previewLayout(isImage: previewIsImage) {
                                case .adaptiveImage:
                                    AdaptiveKFImage(
                                        data: preview,
                                        placeholder: nil,
                                        mediaAspectRatio: previewAspectRatio
                                    )
                                case .aspectFit:
                                    NetworkImage(data: preview)
                                        .scaledToFit()
                                }
                            }
                            .zoomable(min: 1, max: 5, doubleTapGesture: .scale(2))
                            .offset(
                                y: MediaViewerDismissGesturePolicy.verticalOffset(
                                    for: .media,
                                    translationY: dismissOffset
                                )
                            )
                        } else {
                            ProgressView()
                        }
                    }
                } else {
                    // LazyPager 1.2.1 only supports downward dismissal.
                    ZStack(alignment: .bottom) {
                        LazyPager(data: medias, page: pagerSelectedIndex) { media in
                            mediaContent(media)
                                .accessibilityLabel(Text(verbatim: media.accessibleDescription))
                                .contextMenu {
                                    // Video long presses are reserved for temporary double-speed playback.
                                    if !media.isVideoMedia {
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

                        if shouldShowBottomOverlay, !postSheetPresented {
                            bottomOverlay
                                .offset(
                                    y: MediaViewerDismissGesturePolicy.verticalOffset(
                                        for: .bottomOverlay,
                                        translationY: dismissOffset
                                    )
                                )
                                .opacity(opacity)
                        }
                    }
                }
            }
            .ignoresSafeArea()

            MediaViewerDismissGestureHost(
                enabled: !postExpanded,
                onChanged: updateDismissGesture,
                onEnded: endDismissGesture,
                onCancelled: cancelDismissGesture
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .ignoresSafeArea()

            if showData {
                topOverlay
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .opacity(opacity)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .allowsHitTesting(!isDismissing)
                    .zIndex(3)
            }
            if postExpanded {
                Color.black.opacity(0.32)
                    .ignoresSafeArea()
                    .onTapGesture { collapsePost() }
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel(Text("media_post_collapse"))
                    .zIndex(4)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .videoPlaybackPresentation(mediaURLs: medias.map(\.url), selectedMediaURL: selectedMedia?.url)
        .onAppear {
            applyInitialSelectionIfNeeded()
            postSheetPresented = shouldShowPostSheet
        }
        .onChange(of: mediaSignature) { _, _ in
            applyInitialSelectionIfNeeded()
        }
        .onChange(of: selectedIndex) { _, _ in
            isPlaying = true
            videoState = .idle
            playbackRate = 1
        }
        .onChange(of: isLandscapeViewing) { _, newValue in
            MediaOrientationController.setLandscape(newValue)
        }
        .onDisappear {
            if isLandscapeViewing {
                MediaOrientationController.setLandscape(false)
            }
        }
        .onChange(of: shouldShowPostSheet) { _, visible in
            collapsePost()
            postSheetPresented = visible
        }
        .sheet(isPresented: $postSheetPresented) {
            mediaPostSheet
        }
        .background(.black.opacity(opacity))
        .background(ClearFullScreenBackground())
        .toolbar(.hidden, for: .navigationBar)
    }

    @ViewBuilder
    private func mediaContent(_ media: any UiMedia) -> some View {
        switch onEnum(of: media) {
        case .image(let image):
            AdaptiveKFImage(
                data: image.url,
                placeholder: image.previewUrl,
                customHeader: image.customHeaders,
                mediaAspectRatio: CGFloat(image.aspectRatio)
            )
        case .video(let video):
            if medias.indices.contains(selectedIndex), medias[selectedIndex].url == video.url {
                StatusMediaVideoView(
                    data: video,
                    play: $isPlaying,
                    videoState: $videoState,
                    time: timeBinding(for: video.url),
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
            topOverlayButton(width: 44, action: {
                if postExpanded { collapsePost() } else { dismiss() }
            }) {
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
                        .accessibilityLabel(
                            Text("media_menu_download", bundle: .main)
                        )

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
                        .accessibilityLabel(
                            Text("media_menu_download", bundle: .main)
                        )
                    }
                }
                .padding(.horizontal, 4)
                .backport
                .glassEffect(.regularInteractive, in: .capsule, fallbackBackground: .regularMaterial)
            }
        }
        .buttonStyle(.plain)
        .foregroundStyle(.white)
        .safeAreaPadding([.top, .horizontal])
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
                if post == nil, medias.count > 10 {
                    MediaPageSlider(count: medias.count, page: $selectedIndex)
                } else {
                    LazyPagerIndicator(count: medias.count, page: $selectedIndex)
                }
            }

            if let selectedMedia, case .video = onEnum(of: selectedMedia) {
                VideoControlView(
                    isPlaying: $isPlaying,
                    currentTime: timeBinding(for: selectedMedia.url),
                    videoState: videoState,
                    playbackRate: playbackRate
                )
                .id(selectedMedia.url)
            }

        }
    }

    private var shouldShowPostSheet: Bool {
        showPostInMediaViewer && post != nil && showData && !isLandscapeViewing && !isDismissing
    }

    private var postExpanded: Bool {
        postSheetPresented && postDetent == .large
    }

    private func collapsePost() {
        withAnimation {
            postDetent = .height(postSummaryHeight)
        }
    }

    @ViewBuilder
    private var mediaPostSheet: some View {
        if let post {
            let content = VStack(spacing: 0) {
                if postDetent == .large {
                    ScrollView {
                        StatusView(
                            data: post,
                            isDetail: true,
                            isClickable: false,
                            showAttachments: false,
                            showParents: false,
                            quotes: quotes
                        )
                        .padding(.horizontal)
                        .padding(.bottom)
                    }
                    .padding(.top, 24)
                } else {
                    VStack(spacing: 8) {
                        bottomOverlayContent
                        if let user = post.user {
                            UserOnelineView(data: user, showAvatar: true, trailing: { EmptyView() }) {
                                user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                            }
                        }
                        StatusActionsView(data: Array(post.actions), useText: false)
                    }
                    .padding(.horizontal)
                    .padding(.top, 24)
                    .padding(.bottom, 8)
                    .fixedSize(horizontal: false, vertical: true)
                    .onGeometryChange(for: CGFloat.self) { proxy in
                        ceil(proxy.size.height)
                    } action: { height in
                        guard height > 0, height != postSummaryHeight else { return }
                        postSummaryHeight = height
                        if postDetent != .large { postDetent = .height(height) }
                    }
                    Spacer(minLength: 0)
                }
            }

            Group {
                if #available(iOS 26.0, *) {
                    content
                } else {
                    content.presentationBackground(postDetent == .large ? .regularMaterial : .ultraThinMaterial)
                }
            }
            .presentationDetents([.height(postSummaryHeight), .large], selection: $postDetent)
            .presentationDragIndicator(.visible)
            .presentationContentInteraction(.resizes)
            // The viewer supplies the expanded scrim so tapping it collapses, rather than dismisses, the sheet.
            .presentationBackgroundInteraction(.enabled)
            .interactiveDismissDisabled()
            .preferredColorScheme(.dark)
        }
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

    private func timeBinding(for url: String?) -> Binding<CMTime> {
        guard let url else { return .constant(.zero) }
        return Binding(
            get: { playbackTimes[url] ?? CMTime(seconds: MediaPlaybackMemory.shared.position(for: url), preferredTimescale: 600) },
            set: {
                playbackTimes[url] = $0
                VideoPlaybackSession.setPosition(for: url, seconds: $0.seconds)
            }
        )
    }

    private var selectedMedia: (any UiMedia)? {
        guard medias.indices.contains(selectedIndex) else {
            return nil
        }
        return medias[selectedIndex]
    }

    private var selectedMediaIsVideo: Bool {
        selectedMedia?.isVideoMedia == true
    }

    private var shouldShowBottomOverlay: Bool {
        if selectedMediaIsVideo {
            return showData
        }
        return showData && !isLandscapeViewing && (medias.count > 1)
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
        let direction = MediaViewerDismissGesturePolicy.dismissDirection(
            translationY: translationY,
            velocityY: velocityY
        )
        withAnimation(.linear(duration: 0.2)) {
            dismissOffset = direction * containerHeight
            opacity = 0
        } completion: {
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) {
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
    let enabled: Bool
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
        context.coordinator.enabled = enabled
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
        var enabled = true
        var onChanged: (CGFloat, CGFloat) -> Void
        var onEnded: (CGFloat, CGFloat, CGFloat) -> Bool
        var onCancelled: () -> Void

        private weak var sourceView: UIView?
        private weak var installedWindow: UIWindow?
        private weak var touchedZoomScrollView: UIScrollView?
        private weak var touchedVerticalScrollView: UIScrollView?
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
            touchedVerticalScrollView = nil
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

        private func owningViewController(of view: UIView) -> UIViewController? {
            var responder: UIResponder? = view
            while let current = responder {
                if let controller = current as? UIViewController { return controller }
                responder = current.next
            }
            return nil
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard enabled, gestureRecognizer === panRecognizer,
                  let panRecognizer,
                  let sourceView else { return false }
            let velocity = panRecognizer.velocity(in: sourceView)
            guard MediaViewerDismissGesturePolicy.shouldBegin(
                velocityX: velocity.x,
                velocityY: velocity.y,
                zoomScale: touchedZoomScrollView?.zoomScale,
                minimumZoomScale: touchedZoomScrollView?.minimumZoomScale
            ) else {
                return false
            }
            return MediaViewerDismissGesturePolicy.shouldBeginDismissPan(
                in: touchedVerticalScrollView,
                velocityY: velocity.y
            )
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            guard enabled, let sourceView,
                  let window = installedWindow else { return false }
            // The recognizer is installed on UIWindow, so screen bounds alone also include sheets.
            guard let viewer = owningViewController(of: sourceView)?.view,
                  let touchedView = touch.view,
                  MediaViewerDismissGesturePolicy.belongsToViewer(touchedView, viewer: viewer) else {
                return false
            }
            let point = touch.location(in: window)
            guard sourceView.convert(sourceView.bounds, to: window).contains(point) else {
                return false
            }
            let touchedScrollViews = ancestorScrollViews(from: touch.view)
            touchedZoomScrollView = touchedScrollViews.first {
                $0.maximumZoomScale > $0.minimumZoomScale
            }
            touchedVerticalScrollView = touchedScrollViews.first {
                MediaViewerDismissGesturePolicy.hasScrollableVerticalContent($0)
            }
            touchedZoomScrollViewInitialOffset = touchedZoomScrollView?.contentOffset
            return true
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            guard gestureRecognizer === panRecognizer else { return false }
            let velocityY = panRecognizer?.velocity(in: sourceView).y ?? 0
            return MediaViewerDismissGesturePolicy.shouldTakePriority(
                over: otherGestureRecognizer,
                velocityY: velocityY
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
            touchedVerticalScrollView = nil
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
            .accessibilityLabel(
                Text("media_page_position", bundle: .main)
            )
            .accessibilityValue(Text(verbatim: "\(displayedPage + 1) of \(count)"))

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
