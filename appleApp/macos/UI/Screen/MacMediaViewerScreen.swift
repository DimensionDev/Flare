import AppKit
import AVFoundation
import FlareAppleUI
import KotlinSharedUI
import SwiftUI

private let macMediaMinimumZoomScale: CGFloat = 1
private let macMediaMaximumZoomScale: CGFloat = 5

struct MacMediaViewerScreen: View {
    let medias: [any UiMedia]
    let initialIndex: Int
    let preview: String?
    let shareContext: MacMediaShareContext?

    @State private var selectedIndex: Int
    @State private var scrollPosition: Int?
    @State private var zoomScales: [Int: CGFloat] = [:]
    @State private var previewZoomScale: CGFloat = macMediaMinimumZoomScale
    @State private var isPlaying = true
    @State private var videoState: VideoState = .idle
    @State private var currentTime: CMTime = .zero
    @State private var playbackRate: Float = 1
    @State private var didApplyInitialSelection = false
    @State private var isSharingSelectedMedia = false
    @State private var isSavingSelectedMedia = false
    @State private var exportAlert: MacMediaExportAlert?
    @State private var sharePickerAnchor = MacSharePickerAnchorBox()

    init(
        medias: [any UiMedia],
        initialIndex: Int,
        preview: String?,
        shareContext: MacMediaShareContext? = nil
    ) {
        self.medias = medias
        self.initialIndex = initialIndex
        self.preview = preview
        self.shareContext = shareContext
        let initialSelection = max(0, initialIndex)
        self._selectedIndex = .init(initialValue: initialSelection)
        self._scrollPosition = .init(initialValue: initialSelection)
    }

    var body: some View {
        ZStack {
            Color.black

            if medias.isEmpty {
                previewContent
            } else {
                pagerContent
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea()
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text(navigationTitle)
                    .padding(.horizontal)
            }
            ToolbarItemGroup(placement: .navigation) {
                if medias.count > 1 {
                    Button {
                        goToPreviousMedia()
                    } label: {
                        Image(systemName: "chevron.left")
                    }
                    .disabled(!canGoToPreviousMedia)
                    .help(Text(verbatim: "Previous media"))

                    Button {
                        goToNextMedia()
                    } label: {
                        Image(systemName: "chevron.right")
                    }
                    .disabled(!canGoToNextMedia)
                    .help(Text(verbatim: "Next media"))
                }
            }

            ToolbarItemGroup(placement: .primaryAction) {
                if let selectedExportSource {
                    Button {
                        saveSelectedMedia()
                    } label: {
                        if isSavingSelectedMedia {
                            ProgressView()
                                .controlSize(.small)
                        } else {
                            Image(systemName: "square.and.arrow.down")
                        }
                    }
                    .disabled(isSavingSelectedMedia)
                    .help(Text(verbatim: "Save As"))

                    if selectedExportSource.supportsSharing {
                        Button {
                            shareSelectedMedia()
                        } label: {
                            if isSharingSelectedMedia {
                                ProgressView()
                                    .controlSize(.small)
                            } else {
                                Image(systemName: "square.and.arrow.up")
                            }
                        }
                        .background(MacSharePickerAnchorView(anchor: sharePickerAnchor))
                        .disabled(isSharingSelectedMedia)
                        .help(Text(verbatim: "Share"))
                    }
                }

                if selectedMediaIsZoomable {
                    Button {
                        resetSelectedZoom()
                    } label: {
                        Image(systemName: "arrow.down.right.and.arrow.up.left")
                    }
                    .disabled(selectedZoomScale <= macMediaMinimumZoomScale + 0.01)
                    .help(Text(verbatim: "Actual size"))

                    Button {
                        zoomSelectedMedia(by: 0.8)
                    } label: {
                        Image(systemName: "minus.magnifyingglass")
                    }
                    .disabled(selectedZoomScale <= macMediaMinimumZoomScale + 0.01)
                    .help(Text(verbatim: "Zoom out"))

                    Button {
                        zoomSelectedMedia(by: 1.25)
                    } label: {
                        Image(systemName: "plus.magnifyingglass")
                    }
                    .disabled(selectedZoomScale >= macMediaMaximumZoomScale - 0.01)
                    .help(Text(verbatim: "Zoom in"))
                }
            }

        }
        .preferredColorScheme(.dark)
        .alert(item: $exportAlert) { alert in
            Alert(
                title: Text(verbatim: alert.title),
                message: Text(verbatim: alert.message),
                dismissButton: .default(Text(verbatim: "OK"))
            )
        }
        .onAppear {
            applyInitialSelectionIfNeeded()
        }
        .onChange(of: mediaSignature) { _, _ in
            applyInitialSelectionIfNeeded()
            pruneZoomScales()
        }
        .onChange(of: selectedIndex) { _, newValue in
            if scrollPosition != newValue {
                withAnimation(.snappy(duration: 0.24)) {
                    scrollPosition = newValue
                }
            }
            resetPlaybackState()
        }
    }

    private var pagerContent: some View {
        GeometryReader { geometry in
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 0) {
                    ForEach(medias.indices, id: \.self) { index in
                        MacMediaViewerPage(
                            media: medias[index],
                            isSelected: selectedIndex == index,
                            zoomScale: zoomBinding(for: index),
                            isPlaying: $isPlaying,
                            videoState: $videoState,
                            currentTime: $currentTime,
                            playbackRate: $playbackRate
                        )
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .id(index)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollIndicators(.hidden)
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $scrollPosition)
            .scrollDisabled(selectedZoomScale > macMediaMinimumZoomScale + 0.01)
            .onChange(of: scrollPosition) { _, newValue in
                guard let newValue else { return }
                let nextIndex = clampedIndex(newValue, count: medias.count)
                guard nextIndex != selectedIndex else { return }
                DispatchQueue.main.async {
                    selectedIndex = nextIndex
                }
            }
        }
    }

    @ViewBuilder
    private var previewContent: some View {
        if let preview {
            MacZoomableScrollView(
                magnification: $previewZoomScale,
                minimumMagnification: macMediaMinimumZoomScale,
                maximumMagnification: macMediaMaximumZoomScale
            ) {
                NetworkImage(data: preview, contentMode: .fit, usesCrossfade: true)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        } else {
            ProgressView()
                .tint(.white)
        }
    }

    private var selectedMedia: (any UiMedia)? {
        guard medias.indices.contains(selectedIndex) else {
            return nil
        }
        return medias[selectedIndex]
    }

    private var selectedMediaIsZoomable: Bool {
        selectedMedia?.isMacZoomableMedia == true || medias.isEmpty && preview != nil
    }

    private var selectedExportSource: MacMediaExportSource? {
        guard let selectedMedia else {
            return nil
        }
        return MacMediaExportSource(
            media: selectedMedia,
            shareContext: shareContext
        )
    }

    private var selectedZoomScale: CGFloat {
        if medias.isEmpty {
            return previewZoomScale
        }
        return zoomScales[selectedIndex, default: macMediaMinimumZoomScale]
    }

    private var navigationTitle: String {
        guard !medias.isEmpty else {
            return "Media"
        }
        return "\(clampedIndex(selectedIndex, count: medias.count) + 1) / \(medias.count)"
    }

    private var mediaSignature: String {
        medias.map { $0.url }.joined(separator: "\n")
    }

    private var canGoToPreviousMedia: Bool {
        !medias.isEmpty && selectedIndex > 0
    }

    private var canGoToNextMedia: Bool {
        !medias.isEmpty && selectedIndex < medias.count - 1
    }

    private func zoomBinding(for index: Int) -> Binding<CGFloat> {
        Binding(
            get: {
                zoomScales[index, default: macMediaMinimumZoomScale]
            },
            set: { newValue in
                zoomScales[index] = clampedZoomScale(newValue)
            }
        )
    }

    private func applyInitialSelectionIfNeeded() {
        guard !medias.isEmpty else {
            return
        }
        if !didApplyInitialSelection || selectedIndex >= medias.count {
            let initialSelection = clampedIndex(initialIndex, count: medias.count)
            selectedIndex = initialSelection
            scrollPosition = initialSelection
            didApplyInitialSelection = true
        }
    }

    private func pruneZoomScales() {
        zoomScales = zoomScales.filter { medias.indices.contains($0.key) }
    }

    private func resetPlaybackState() {
        isPlaying = true
        videoState = .idle
        currentTime = .zero
        playbackRate = 1
    }

    private func goToPreviousMedia() {
        goToMedia(selectedIndex - 1)
    }

    private func goToNextMedia() {
        goToMedia(selectedIndex + 1)
    }

    private func goToMedia(_ index: Int) {
        let nextIndex = clampedIndex(index, count: medias.count)
        guard nextIndex != selectedIndex else {
            return
        }
        withAnimation(.snappy(duration: 0.24)) {
            selectedIndex = nextIndex
            scrollPosition = nextIndex
        }
    }

    private func resetSelectedZoom() {
        setSelectedZoom(macMediaMinimumZoomScale)
    }

    private func zoomSelectedMedia(by multiplier: CGFloat) {
        setSelectedZoom(selectedZoomScale * multiplier)
    }

    private func setSelectedZoom(_ scale: CGFloat) {
        let nextScale = clampedZoomScale(scale)
        if medias.isEmpty {
            previewZoomScale = nextScale
        } else {
            zoomScales[selectedIndex] = nextScale
        }
    }

    private func saveSelectedMedia() {
        guard !isSavingSelectedMedia,
              let source = selectedExportSource else {
            return
        }

        isSavingSelectedMedia = true
        Task {
            do {
                _ = try await MacMediaFileExporter.save(
                    source: source
                )
            } catch {
                exportAlert = MacMediaExportAlert(
                    title: "Save Failed",
                    message: error.localizedDescription
                )
            }
            isSavingSelectedMedia = false
        }
    }

    private func shareSelectedMedia() {
        guard !isSharingSelectedMedia,
              let source = selectedExportSource,
              source.supportsSharing else {
            return
        }

        isSharingSelectedMedia = true
        Task {
            do {
                let fileURL = try await MacMediaFileExporter.makeShareFile(source: source)
                guard selectedExportSource?.id == source.id else {
                    isSharingSelectedMedia = false
                    return
                }
                try MacMediaFileExporter.presentSharePicker(
                    fileURL: fileURL,
                    relativeTo: sharePickerAnchor.view
                )
            } catch {
                exportAlert = MacMediaExportAlert(
                    title: "Share Failed",
                    message: error.localizedDescription
                )
            }
            isSharingSelectedMedia = false
        }
    }

    private func clampedZoomScale(_ scale: CGFloat) -> CGFloat {
        min(max(scale, macMediaMinimumZoomScale), macMediaMaximumZoomScale)
    }

    private func clampedIndex(_ index: Int, count: Int) -> Int {
        guard count > 0 else {
            return 0
        }
        return min(max(index, 0), count - 1)
    }
}

private struct MacMediaViewerPage: View {
    let media: any UiMedia
    let isSelected: Bool
    @Binding var zoomScale: CGFloat
    @Binding var isPlaying: Bool
    @Binding var videoState: VideoState
    @Binding var currentTime: CMTime
    @Binding var playbackRate: Float
    @State private var imageLoadingProgress: Double?

    var body: some View {
        ZStack {
            Color.black
            mediaContent
        }
        .contentShape(Rectangle())
        .overlay(alignment: .bottomLeading) {
            imageLoadingProgressView
        }
        .onChange(of: media.url) { _, _ in
            resetImageLoadingProgress()
        }
        .onChange(of: isSelected) { _, newValue in
            if !newValue {
                resetImageLoadingProgress()
            }
        }
        .onDisappear {
            resetImageLoadingProgress()
        }
    }

    @ViewBuilder
    private var mediaContent: some View {
        switch onEnum(of: media) {
        case .image(let image):
            zoomableImage(url: image.url, preview: image.previewUrl, customHeaders: image.customHeaders)
        case .gif(let gif):
            zoomableImage(url: gif.url, preview: gif.previewUrl, customHeaders: gif.customHeaders)
        case .video(let video):
            if isSelected {
                StatusMediaVideoView(
                    data: video,
                    play: $isPlaying,
                    videoState: $videoState,
                    time: $currentTime,
                    playbackRate: $playbackRate
                )
                .padding(24)
            } else {
                NetworkImage(data: video.thumbnailUrl, customHeader: video.customHeaders, contentMode: .fit)
                    .padding(24)
            }
        case .audio:
            Image(systemName: "waveform")
                .font(.system(size: 48, weight: .medium))
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var imageLoadingProgressView: some View {
        if isSelected,
           let imageLoadingProgress,
           imageLoadingProgress < 0.999 {
            ProgressView(value: imageLoadingProgress, total: 1)
                .progressViewStyle(.circular)
                .controlSize(.small)
                .tint(.white)
                .labelsHidden()
                .frame(width: 28, height: 28)
                .padding(10)
                .background(.black.opacity(0.58), in: RoundedRectangle(cornerRadius: 8))
                .padding(.leading, 18)
                .padding(.bottom, 18)
                .allowsHitTesting(false)
                .accessibilityLabel(Text(verbatim: "Image loading progress"))
        }
    }

    private func zoomableImage(
        url: String,
        preview: String,
        customHeaders: [String: String]?
    ) -> some View {
        MacZoomableScrollView(
            magnification: $zoomScale,
            minimumMagnification: macMediaMinimumZoomScale,
            maximumMagnification: macMediaMaximumZoomScale
        ) {
            if preview.isEmpty || preview == url {
                NetworkImage(
                    data: url,
                    customHeader: customHeaders,
                    contentMode: .fit,
                    usesCrossfade: true,
                    onProgress: handleImageLoadingProgress
                )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                NetworkImage(
                    data: url,
                    placeholder: preview,
                    customHeader: customHeaders,
                    contentMode: .fit,
                    usesCrossfade: true,
                    onProgress: handleImageLoadingProgress
                )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }

    private func handleImageLoadingProgress(_ progress: Double?) {
        Task { @MainActor in
            guard isSelected else {
                return
            }
            guard let progress else {
                imageLoadingProgress = nil
                return
            }
            let clampedProgress = min(max(progress, 0), 1)
            imageLoadingProgress = clampedProgress < 0.999 ? clampedProgress : nil
        }
    }

    private func resetImageLoadingProgress() {
        imageLoadingProgress = nil
    }
}

private struct MacZoomableScrollView<Content: View>: NSViewRepresentable {
    @Binding var magnification: CGFloat
    let minimumMagnification: CGFloat
    let maximumMagnification: CGFloat
    let content: Content

    init(
        magnification: Binding<CGFloat>,
        minimumMagnification: CGFloat,
        maximumMagnification: CGFloat,
        @ViewBuilder content: () -> Content
    ) {
        self._magnification = magnification
        self.minimumMagnification = minimumMagnification
        self.maximumMagnification = maximumMagnification
        self.content = content()
    }

    func makeCoordinator() -> MacZoomableScrollCoordinator {
        MacZoomableScrollCoordinator(magnification: $magnification)
    }

    func makeNSView(context: Context) -> FlareZoomScrollView {
        let scrollView = FlareZoomScrollView()
        scrollView.drawsBackground = false
        scrollView.borderType = .noBorder
        scrollView.hasHorizontalScroller = false
        scrollView.hasVerticalScroller = false
        scrollView.autohidesScrollers = true
        scrollView.scrollerStyle = .overlay
        scrollView.allowsMagnification = false
        scrollView.usesPredominantAxisScrolling = false

        context.coordinator.updateConfiguration(
            minimumMagnification: minimumMagnification,
            maximumMagnification: maximumMagnification
        )
        context.coordinator.install(content: content, in: scrollView)
        scrollView.onViewportLayout = { [weak coordinator = context.coordinator, weak scrollView] size in
            guard let scrollView else { return }
            coordinator?.resizeDocument(to: size, in: scrollView)
        }
        scrollView.onMagnify = { [weak coordinator = context.coordinator, weak scrollView] delta, point, viewportOffset in
            guard let scrollView else { return }
            coordinator?.magnify(
                by: delta,
                in: scrollView,
                anchoredAt: point,
                viewportOffset: viewportOffset
            )
        }
        scrollView.onToggleZoom = { [weak coordinator = context.coordinator, weak scrollView] point in
            guard let scrollView else { return }
            coordinator?.toggleZoom(in: scrollView, centeredAt: point)
        }

        return scrollView
    }

    func updateNSView(_ nsView: FlareZoomScrollView, context: Context) {
        context.coordinator.updateConfiguration(
            minimumMagnification: minimumMagnification,
            maximumMagnification: maximumMagnification
        )
        context.coordinator.update(content: content, in: nsView)

        let targetMagnification = clamped(magnification)
        if abs(context.coordinator.currentMagnification - targetMagnification) > 0.005 {
            context.coordinator.setMagnification(targetMagnification, in: nsView, publishesStateChange: false)
        }
    }

    private func clamped(_ scale: CGFloat) -> CGFloat {
        min(max(scale, minimumMagnification), maximumMagnification)
    }

}

// Keep this coordinator non-generic to avoid a Swift 6.3 Release optimizer crash in deinit.
private final class MacZoomableScrollCoordinator {
    private let magnification: Binding<CGFloat>
    private let documentView = NSView()
    private var hostingView: NSHostingView<AnyView>?
    private var minimumMagnification = macMediaMinimumZoomScale
    private var maximumMagnification = macMediaMaximumZoomScale
    private(set) var currentMagnification = macMediaMinimumZoomScale

    init(magnification: Binding<CGFloat>) {
        self.magnification = magnification
        self.currentMagnification = magnification.wrappedValue
        documentView.wantsLayer = true
        documentView.layer?.backgroundColor = NSColor.clear.cgColor
    }

    func updateConfiguration(
        minimumMagnification: CGFloat,
        maximumMagnification: CGFloat
    ) {
        self.minimumMagnification = minimumMagnification
        self.maximumMagnification = maximumMagnification
    }

    func install<Content: View>(content: Content, in scrollView: FlareZoomScrollView) {
        let hostingView = NSHostingView(rootView: AnyView(content))
        hostingView.autoresizingMask = [.width, .height]
        hostingView.frame = documentView.bounds
        hostingView.layer?.backgroundColor = NSColor.clear.cgColor
        documentView.addSubview(hostingView)
        self.hostingView = hostingView
        scrollView.documentView = documentView
        resizeDocument(to: scrollView.contentView.bounds.size, in: scrollView)
    }

    func update<Content: View>(content: Content, in scrollView: FlareZoomScrollView) {
        hostingView?.rootView = AnyView(content)
        resizeDocument(to: scrollView.contentView.bounds.size, in: scrollView)
    }

    func resizeDocument(to size: NSSize, in scrollView: FlareZoomScrollView) {
        guard size.width > 0, size.height > 0 else { return }
        layoutDocument(
            viewportSize: size,
            scale: currentMagnification,
            in: scrollView,
            centeredAt: nil
        )
    }

    func magnify(
        by delta: CGFloat,
        in scrollView: FlareZoomScrollView,
        anchoredAt point: NSPoint,
        viewportOffset: NSPoint
    ) {
        let multiplier = max(0.2, 1 + delta)
        setMagnification(
            currentMagnification * multiplier,
            in: scrollView,
            centeredAt: point,
            viewportOffset: viewportOffset,
            publishesStateChange: true
        )
    }

    func setMagnification(
        _ scale: CGFloat,
        in scrollView: FlareZoomScrollView,
        publishesStateChange: Bool
    ) {
        setMagnification(
            scale,
            in: scrollView,
            centeredAt: documentCenter(in: scrollView),
            publishesStateChange: publishesStateChange
        )
    }

    func toggleZoom(in scrollView: FlareZoomScrollView, centeredAt point: NSPoint) {
        let nextScale = currentMagnification <= minimumMagnification + 0.01 ? 2 : minimumMagnification
        setMagnification(
            nextScale,
            in: scrollView,
            centeredAt: point,
            publishesStateChange: true
        )
    }

    private func setMagnification(
        _ scale: CGFloat,
        in scrollView: FlareZoomScrollView,
        centeredAt point: NSPoint,
        viewportOffset: NSPoint? = nil,
        publishesStateChange: Bool
    ) {
        let clampedScale = clamped(scale)
        guard abs(currentMagnification - clampedScale) > 0.005 else {
            return
        }

        let viewportSize = scrollView.contentView.bounds.size
        guard viewportSize.width > 0, viewportSize.height > 0 else {
            currentMagnification = clampedScale
            if publishesStateChange {
                publishMagnification(clampedScale)
            }
            return
        }

        currentMagnification = clampedScale
        layoutDocument(
            viewportSize: viewportSize,
            scale: clampedScale,
            in: scrollView,
            centeredAt: point,
            viewportOffset: viewportOffset
        )

        if publishesStateChange {
            publishMagnification(clampedScale)
        }
    }

    private func layoutDocument(
        viewportSize: NSSize,
        scale: CGFloat,
        in scrollView: FlareZoomScrollView,
        centeredAt point: NSPoint?,
        viewportOffset: NSPoint? = nil
    ) {
        let oldDocumentSize = documentView.bounds.size
        let oldVisibleRect = scrollView.contentView.bounds
        let anchor = point ?? NSPoint(x: oldVisibleRect.midX, y: oldVisibleRect.midY)
        let anchorRatio = CGPoint(
            x: oldDocumentSize.width > 0 ? anchor.x / oldDocumentSize.width : 0.5,
            y: oldDocumentSize.height > 0 ? anchor.y / oldDocumentSize.height : 0.5
        )
        let newDocumentSize = NSSize(
            width: max(viewportSize.width, viewportSize.width * scale),
            height: max(viewportSize.height, viewportSize.height * scale)
        )

        documentView.frame = NSRect(origin: .zero, size: newDocumentSize)
        hostingView?.frame = documentView.bounds

        let newAnchor = NSPoint(
            x: newDocumentSize.width * anchorRatio.x,
            y: newDocumentSize.height * anchorRatio.y
        )
        let visibleOffset = clampedViewportOffset(
            viewportOffset ?? NSPoint(x: viewportSize.width / 2, y: viewportSize.height / 2),
            viewportSize: viewportSize
        )
        let proposedOrigin = NSPoint(
            x: newAnchor.x - visibleOffset.x,
            y: newAnchor.y - visibleOffset.y
        )
        scrollView.contentView.scroll(to: clampedVisibleOrigin(
            proposedOrigin,
            viewportSize: viewportSize,
            documentSize: newDocumentSize
        ))
        scrollView.reflectScrolledClipView(scrollView.contentView)
        scrollView.invalidateDragCursorRects()
        scrollView.contentView.needsDisplay = true
    }

    private func clampedVisibleOrigin(
        _ origin: NSPoint,
        viewportSize: NSSize,
        documentSize: NSSize
    ) -> NSPoint {
        NSPoint(
            x: min(max(origin.x, 0), max(documentSize.width - viewportSize.width, 0)),
            y: min(max(origin.y, 0), max(documentSize.height - viewportSize.height, 0))
        )
    }

    private func clampedViewportOffset(
        _ offset: NSPoint,
        viewportSize: NSSize
    ) -> NSPoint {
        NSPoint(
            x: min(max(offset.x, 0), viewportSize.width),
            y: min(max(offset.y, 0), viewportSize.height)
        )
    }

    private func documentCenter(in scrollView: FlareZoomScrollView) -> NSPoint {
        let visibleRect = scrollView.contentView.bounds
        return NSPoint(x: visibleRect.midX, y: visibleRect.midY)
    }

    private func clamped(_ scale: CGFloat) -> CGFloat {
        min(max(scale, minimumMagnification), maximumMagnification)
    }

    private func publishMagnification(_ scale: CGFloat) {
        guard abs(magnification.wrappedValue - scale) > 0.005 else {
            return
        }
        magnification.wrappedValue = scale
    }
}

private final class FlareZoomScrollView: NSScrollView {
    var onMagnify: ((CGFloat, NSPoint, NSPoint) -> Void)?
    var onViewportLayout: ((NSSize) -> Void)?
    var onToggleZoom: ((NSPoint) -> Void)?
    private var dragStartLocationInWindow: NSPoint?
    private var dragStartVisibleOrigin: NSPoint?
    private var windowMovableByBackgroundBeforeDrag: Bool?

    override var mouseDownCanMoveWindow: Bool {
        !canDragContent
    }

    override func layout() {
        super.layout()
        onViewportLayout?(contentView.bounds.size)
    }

    override func magnify(with event: NSEvent) {
        guard let documentView else {
            return
        }
        let point = documentView.convert(event.locationInWindow, from: nil)
        let visibleRect = contentView.bounds
        let viewportOffset = NSPoint(
            x: point.x - visibleRect.minX,
            y: point.y - visibleRect.minY
        )
        onMagnify?(event.magnification, point, viewportOffset)
    }

    override func smartMagnify(with event: NSEvent) {
        toggleZoom(with: event)
    }

    override func mouseDown(with event: NSEvent) {
        if event.clickCount == 2 {
            toggleZoom(with: event)
        } else if canDragContent {
            dragStartLocationInWindow = event.locationInWindow
            dragStartVisibleOrigin = contentView.bounds.origin
            windowMovableByBackgroundBeforeDrag = window?.isMovableByWindowBackground
            window?.isMovableByWindowBackground = false
            NSCursor.closedHand.set()
        } else {
            super.mouseDown(with: event)
        }
    }

    override func mouseDragged(with event: NSEvent) {
        guard canDragContent,
              let documentView,
              let dragStartLocationInWindow,
              let dragStartVisibleOrigin else {
            clearDragState()
            super.mouseDragged(with: event)
            return
        }

        let viewportSize = contentView.bounds.size
        let documentSize = documentView.bounds.size
        let delta = NSPoint(
            x: event.locationInWindow.x - dragStartLocationInWindow.x,
            y: event.locationInWindow.y - dragStartLocationInWindow.y
        )
        let proposedOrigin = NSPoint(
            x: dragStartVisibleOrigin.x - (documentSize.width > viewportSize.width ? delta.x : 0),
            y: dragStartVisibleOrigin.y - (documentSize.height > viewportSize.height ? delta.y : 0)
        )

        contentView.scroll(to: clampedVisibleOrigin(
            proposedOrigin,
            viewportSize: viewportSize,
            documentSize: documentSize
        ))
        reflectScrolledClipView(contentView)
        contentView.needsDisplay = true
    }

    override func mouseUp(with event: NSEvent) {
        if dragStartLocationInWindow != nil {
            clearDragState()
            if canDragContent {
                NSCursor.openHand.set()
            }
        } else {
            super.mouseUp(with: event)
        }
    }

    override func resetCursorRects() {
        super.resetCursorRects()
        if canDragContent {
            addCursorRect(bounds, cursor: .openHand)
        }
    }

    override func scrollWheel(with event: NSEvent) {
        if let nextResponder,
           let documentView,
           contentView.bounds.size == documentView.bounds.size {
            nextResponder.scrollWheel(with: event)
        } else {
            super.scrollWheel(with: event)
        }
    }

    func invalidateDragCursorRects() {
        window?.invalidateCursorRects(for: self)
    }

    private func toggleZoom(with event: NSEvent) {
        guard let documentView else {
            return
        }
        let point = documentView.convert(event.locationInWindow, from: nil)
        onToggleZoom?(point)
    }

    private var canDragContent: Bool {
        guard let documentView else {
            return false
        }
        let viewportSize = contentView.bounds.size
        let documentSize = documentView.bounds.size
        return documentSize.width > viewportSize.width + 0.5 ||
            documentSize.height > viewportSize.height + 0.5
    }

    private func clampedVisibleOrigin(
        _ origin: NSPoint,
        viewportSize: NSSize,
        documentSize: NSSize
    ) -> NSPoint {
        NSPoint(
            x: min(max(origin.x, 0), max(documentSize.width - viewportSize.width, 0)),
            y: min(max(origin.y, 0), max(documentSize.height - viewportSize.height, 0))
        )
    }

    private func clearDragState() {
        if let windowMovableByBackgroundBeforeDrag {
            window?.isMovableByWindowBackground = windowMovableByBackgroundBeforeDrag
        }
        dragStartLocationInWindow = nil
        dragStartVisibleOrigin = nil
        windowMovableByBackgroundBeforeDrag = nil
    }
}

private extension UiMedia {
    var isMacZoomableMedia: Bool {
        switch onEnum(of: self) {
        case .image, .gif:
            true
        case .video, .audio:
            false
        }
    }
}

private extension MacMediaExportSource {
    init?(
        media: any UiMedia,
        shareContext: MacMediaShareContext?
    ) {
        switch onEnum(of: media) {
        case .image(let image):
            self.init(
                kind: .image,
                url: image.url,
                customHeaders: image.customHeaders,
                shareContext: shareContext
            )
        case .gif(let gif):
            self.init(
                kind: .gif,
                url: gif.url,
                customHeaders: gif.customHeaders,
                shareContext: shareContext
            )
        case .video(let video):
            self.init(
                kind: .video,
                url: video.url,
                customHeaders: video.customHeaders,
                shareContext: shareContext
            )
        case .audio:
            return nil
        }
    }
}

private struct MacMediaExportAlert: Identifiable {
    let id = UUID()
    let title: String
    let message: String
}

private final class MacSharePickerAnchorBox {
    weak var view: NSView?
}

private struct MacSharePickerAnchorView: NSViewRepresentable {
    let anchor: MacSharePickerAnchorBox

    func makeCoordinator() -> Coordinator {
        Coordinator(anchor: anchor)
    }

    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        context.coordinator.update(view: view)
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        context.coordinator.update(view: nsView)
    }

    static func dismantleNSView(_ nsView: NSView, coordinator: Coordinator) {
        coordinator.clear(view: nsView)
    }

    final class Coordinator {
        private weak var anchor: MacSharePickerAnchorBox?

        init(anchor: MacSharePickerAnchorBox) {
            self.anchor = anchor
        }

        func update(view: NSView) {
            anchor?.view = view
        }

        func clear(view: NSView) {
            if anchor?.view === view {
                anchor?.view = nil
            }
        }
    }
}
