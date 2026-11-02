import SwiftUI
import Kingfisher
import KotlinSharedUI
import FlareAppleCore

public struct NetworkImage: View {
    @Environment(\.shareImageLoading) private var shareImageLoading
    @State private var shareImageID = UUID()
    private let data: UiMedia?
    private let placeholder: UiMedia?
    private let contentMode: SwiftUI.ContentMode
    private let usesCrossfade: Bool
    private let onProgress: ((Double?) -> Void)?
    private let onImageSize: ((CGSize) -> Void)?
    private let onImageFailure: (() -> Void)?
    private var downsamplingEnabled = true
    private let keepsCurrentImage: Bool
    @State private var prepared: PreparedMediaImage?
    @State private var preparedIdentity: String?

    public init(data: UiMedia?, placeholder: UiMedia? = nil,
                contentMode: SwiftUI.ContentMode = .fill, usesCrossfade: Bool = false,
                onProgress: ((Double?) -> Void)? = nil, onImageFailure: (() -> Void)? = nil, onImageSize: ((CGSize) -> Void)? = nil,
                keepsCurrentImage: Bool = false) {
        self.data = data
        self.placeholder = placeholder
        self.contentMode = contentMode
        self.usesCrossfade = usesCrossfade
        self.onProgress = onProgress
        self.onImageFailure = onImageFailure
        self.onImageSize = onImageSize
        self.keepsCurrentImage = keepsCurrentImage
    }

    public var body: some View {
        let identity = "\(String(describing: data?.proxy))|\(data?.cacheKey ?? "")"
        let taskIdentity = "\(identity)|\(ProxyMediaImages.shared.revision)"
        Group {
            if let prepared, preparedIdentity == identity || keepsCurrentImage {
                ResolvedNetworkImage(source: prepared.source, downloader: prepared.downloader, redirectHandler: prepared.redirectHandler,
                    mediaPlaceholder: placeholder, data: data.flatMap { URL(string: $0.url) },
                    customHeader: data?.customHeaders, contentMode: contentMode,
                    usesCrossfade: usesCrossfade && !keepsCurrentImage, onProgress: onProgress, onImageSize: onImageSize,
                    onImageFailure: preparedIdentity == identity ? onImageFailure : nil,
                    downsamplingEnabled: downsamplingEnabled, shareImageID: shareImageID, keepsCurrentImage: keepsCurrentImage)
                    .id(keepsCurrentImage ? shareImageID.uuidString : prepared.key)
            } else {
                Rectangle().fill(.placeholder).redacted(reason: .placeholder)
            }
        }
        .onAppear { if data != nil { shareImageLoading?.register(shareImageID) } }
        .onDisappear { shareImageLoading?.remove(shareImageID) }
        .task(id: taskIdentity) {
            guard let data else { prepared = nil; return }
            do {
                if keepsCurrentImage { onProgress?(0) }
                let result = try await ProxyMediaImages.shared.prepare(data)
                guard !Task.isCancelled else { return }
                if preparedIdentity != identity || prepared?.key != result.key {
                    prepared = result
                }
                preparedIdentity = identity
            } catch {
                guard !Task.isCancelled else { return }
                if !keepsCurrentImage { prepared = nil }
                shareImageLoading?.complete(shareImageID, success: false)
                onProgress?(nil)
                onImageFailure?()
            }
        }
    }
}

private struct ResolvedNetworkImage: View {
    let source: Source
    let downloader: ImageDownloader
    let redirectHandler: AnyRedirectHandler
    let mediaPlaceholder: UiMedia?
    let data: URL?
    let customHeader: [String: String]?
    let contentMode: SwiftUI.ContentMode
    let usesCrossfade: Bool
    let onProgress: ((Double?) -> Void)?
    let onImageSize: ((CGSize) -> Void)?
    var onImageFailure: (() -> Void)? = nil
    let downsamplingEnabled: Bool
    let shareImageID: UUID
    let keepsCurrentImage: Bool
    @Environment(\.shareImageLoading) private var shareImageLoading
    @Environment(\.displayScale) private var displayScale
    @State private var measuredPixels: CGSize = .zero
    @State private var requestedPixels: CGSize = .zero
    @State private var previousImage: (url: URL?, image: KFCrossPlatformImage)?

    public var body: some View {
        NetworkImageBoundsLayout {
            if AppleMediaFileExtension.isGIF(url: data) {
                KFAnimatedImage(source: source)
                    .downloader(downloader)
                    .cacheMemoryOnly(data.map(ImageTranslationExportFile.isOwned) == true)
                    .redirectHandler(redirectHandler)
                    .configure { view in
                        view.needsPrescaling = downsamplingEnabled
                        #if os(iOS)
                        view.contentMode = contentMode == .fill ? .scaleAspectFill : .scaleAspectFit
                        #endif
                    }
                    .flareAsyncCacheTypeCheck()
                    .loadDiskFileSynchronously(false)
                    .fade(duration: keepsCurrentImage ? 0 : (shareImageLoading == nil ? 0.25 : 0))
                    .requestModifier { request in
                        customHeader?.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
                    }
                    .onProgress { receivedSize, totalSize in
                        reportProgress(receivedSize: receivedSize, totalSize: totalSize)
                    }
                    .onSuccess { result in
                        onImageSize?(result.image.size)
                        shareImageLoading?.complete(shareImageID, success: true)
                        onProgress?(1)
                    }
                    .onFailure { error in
                        if !error.isTaskCancelled { onImageFailure?() }
                        if data != nil { shareImageLoading?.complete(shareImageID, success: false) }
                        onProgress?(nil)
                    }
                    .placeholder { placeholderView }
                    .cancelOnDisappear(true)
                    .aspectRatio(contentMode: contentMode)
            } else if downsamplingEnabled && (requestedPixels.width <= 0 || requestedPixels.height <= 0) {
                placeholderView
            } else if usesCrossfade && shareImageLoading == nil {
                CrossfadeNetworkImage(
                    source: source, downloader: downloader, redirectHandler: redirectHandler,
                    mediaPlaceholder: mediaPlaceholder, data: data, customHeader: customHeader,
                    contentMode: contentMode, onProgress: onProgress, onImageSize: onImageSize, onImageFailure: onImageFailure,
                    processor: processor, displayScale: displayScale
                )
            } else {
                KFImage(source: source)
                    .downloader(downloader)
                    .cacheMemoryOnly(data.map(ImageTranslationExportFile.isOwned) == true)
                    .redirectHandler(redirectHandler)
                    .setProcessor(processor)
                    .scaleFactor(displayScale)
                    .cacheOriginalImage()
                    .flareAsyncCacheTypeCheck()
                    .flareBackgroundDecodeIfSupported()
                    .loadDiskFileSynchronously(false)
                    .resizable()
                    .fade(duration: keepsCurrentImage ? 0 : (shareImageLoading == nil ? 0.25 : 0))
                    .requestModifier { request in
                        customHeader?.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
                    }
                    .onProgress { receivedSize, totalSize in
                        reportProgress(receivedSize: receivedSize, totalSize: totalSize)
                    }
                    .onSuccess { result in
                        previousImage = (data, result.image)
                        onImageSize?(result.image.size)
                        shareImageLoading?.complete(shareImageID, success: true)
                        onProgress?(1)
                    }
                    .onFailure { error in
                        if !error.isTaskCancelled { onImageFailure?() }
                        if data != nil { shareImageLoading?.complete(shareImageID, success: false) }
                        onProgress?(nil)
                    }
                    .placeholder { placeholderView }
                    .cancelOnDisappear(true)
                    .aspectRatio(contentMode: contentMode)
            }
        }
        .onGeometryChange(for: CGSize.self) { [displayScale] proxy in
            CGSize(width: ceil(proxy.size.width * displayScale), height: ceil(proxy.size.height * displayScale))
        } action: { size in
            measuredPixels = size
            requestedPixels = CGSize(width: max(requestedPixels.width, size.width),
                                     height: max(requestedPixels.height, size.height))
        }
        .onChange(of: data) { _, _ in
            requestedPixels = measuredPixels
            if !keepsCurrentImage { previousImage = nil }
        }
    }

    private var processor: any ImageProcessor {
        downsamplingEnabled
            ? ImageDownsamplingProcessor(pixelSize: requestedPixels, fillsBounds: contentMode == .fill)
            : DefaultImageProcessor.default
    }

    @ViewBuilder
    private var placeholderView: some View {
        if let previousImage, previousImage.url == data || keepsCurrentImage {
            #if os(macOS)
            Image(nsImage: previousImage.image).resizable()
            #else
            Image(uiImage: previousImage.image).resizable()
            #endif
        } else if let mediaPlaceholder {
            NetworkImage(data: mediaPlaceholder, contentMode: contentMode)
        } else {
            Rectangle().fill(.placeholder).redacted(reason: .placeholder)
        }
    }

    private func reportProgress(receivedSize: Int64, totalSize: Int64) {
        guard totalSize > 0 else {
            onProgress?(nil)
            return
        }
        onProgress?(min(max(Double(receivedSize) / Double(totalSize), 0), 1))
    }
}

// Aspect-fill pixels can overflow their proposal; measure the viewport, not that overflow.
private struct NetworkImageBoundsLayout: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let size = subviews[0].sizeThatFits(proposal)
        return CGSize(width: min(size.width, proposal.width ?? size.width),
                      height: min(size.height, proposal.height ?? size.height))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews[0].place(at: CGPoint(x: bounds.midX, y: bounds.midY), anchor: .center,
                          proposal: ProposedViewSize(bounds.size))
    }
}

private struct CrossfadeNetworkImage: View {
    let source: Source
    let downloader: ImageDownloader
    let redirectHandler: AnyRedirectHandler
    let mediaPlaceholder: UiMedia?
    let data: URL?
    let customHeader: [String: String]?
    let contentMode: SwiftUI.ContentMode
    let onProgress: ((Double?) -> Void)?
    let onImageSize: ((CGSize) -> Void)?
    var onImageFailure: (() -> Void)? = nil
    let processor: any ImageProcessor
    let displayScale: CGFloat

    @State private var loadedURL: URL?
    @State private var loadedImage: KFCrossPlatformImage?
    @State private var loadedProcessorID: String?
    @State private var showsLoadedImage = false
    @State private var keepsPlaceholder = true
    @State private var placeholderRemovalTask: Task<Void, Never>?
    private let transitionDuration: TimeInterval = 0.22

    var body: some View {
        ZStack {
            if keepsPlaceholder || loadedURL != data || loadedProcessorID != processor.identifier {
                placeholderView
            }

            KFImage(source: source)
                .downloader(downloader)
                .cacheMemoryOnly(data.map(ImageTranslationExportFile.isOwned) == true)
                .redirectHandler(redirectHandler)
                .setProcessor(processor)
                .scaleFactor(displayScale)
                .cacheOriginalImage()
                .flareAsyncCacheTypeCheck()
                .flareBackgroundDecodeIfSupported()
                .loadDiskFileSynchronously(false)
                .resizable()
                .requestModifier { request in
                    if let customHeader {
                        for (key, value) in customHeader {
                            request.setValue(value, forHTTPHeaderField: key)
                        }
                    }
                }
                .cancelOnDisappear(true)
                .onProgress { receivedSize, totalSize in
                    reportProgress(receivedSize: receivedSize, totalSize: totalSize)
                }
                .onSuccess { result in
                    onProgress?(1)
                    onImageSize?(result.image.size)
                    loadedURL = data
                    loadedImage = result.image
                    loadedProcessorID = processor.identifier
                    keepsPlaceholder = true
                    withAnimation(.easeInOut(duration: transitionDuration)) {
                        showsLoadedImage = true
                    }
                    schedulePlaceholderRemoval(for: data)
                }
                .onFailure { error in
                    if !error.isTaskCancelled { onImageFailure?() }
                    onProgress?(nil)
                    placeholderRemovalTask?.cancel()
                    keepsPlaceholder = true
                    showsLoadedImage = false
                }
                .aspectRatio(contentMode: contentMode)
                .opacity(showsLoadedImage ? 1 : 0)
        }
        .onAppear {
            resetLoadingStateIfNeeded()
        }
        .onChange(of: data) { _, _ in
            resetLoadingStateIfNeeded()
        }
        .onDisappear {
            placeholderRemovalTask?.cancel()
            placeholderRemovalTask = nil
        }
    }

    @ViewBuilder
    private var placeholderView: some View {
        if let loadedImage, loadedURL == data, loadedProcessorID != processor.identifier {
            #if os(macOS)
            Image(nsImage: loadedImage).resizable().aspectRatio(contentMode: contentMode)
            #else
            Image(uiImage: loadedImage).resizable().aspectRatio(contentMode: contentMode)
            #endif
        } else if let mediaPlaceholder {
            NetworkImage(
                data: mediaPlaceholder,
                contentMode: contentMode
            )
        } else {
            Rectangle()
                .fill(.placeholder)
                .redacted(reason: .placeholder)
        }
    }

    private func resetLoadingStateIfNeeded() {
        guard loadedURL != data else {
            return
        }
        placeholderRemovalTask?.cancel()
        placeholderRemovalTask = nil
        loadedURL = nil
        loadedImage = nil
        loadedProcessorID = nil
        showsLoadedImage = false
        keepsPlaceholder = true
    }

    private func schedulePlaceholderRemoval(for url: URL?) {
        placeholderRemovalTask?.cancel()
        placeholderRemovalTask = Task { @MainActor in
            let nanoseconds = UInt64((transitionDuration + 0.04) * 1_000_000_000)
            try? await Task.sleep(nanoseconds: nanoseconds)
            guard !Task.isCancelled,
                  loadedURL == url,
                  showsLoadedImage else {
                return
            }
            keepsPlaceholder = false
            placeholderRemovalTask = nil
        }
    }

    private func reportProgress(receivedSize: Int64, totalSize: Int64) {
        guard totalSize > 0 else {
            onProgress?(nil)
            return
        }
        onProgress?(min(max(Double(receivedSize) / Double(totalSize), 0), 1))
    }
}

extension KFImageProtocol {
    func flareAsyncCacheTypeCheck() -> Self {
        let result = copyForMutation()
        result.options.asyncCacheTypeCheck = true
        return result
    }

    func flareBackgroundDecodeIfSupported() -> Self {
        #if os(macOS)
        return self
        #else
        return backgroundDecode()
        #endif
    }
}

public extension NetworkImage {
    /// Keep source pixels for views that support zooming through a transform.
    func originalImage() -> Self {
        var result = self
        result.downsamplingEnabled = false
        return result
    }
}
