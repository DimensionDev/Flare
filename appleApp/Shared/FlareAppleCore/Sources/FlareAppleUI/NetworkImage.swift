import SwiftUI
import Kingfisher

public struct NetworkImage: View {
    private let data: URL?
    private let placeholder: URL?
    private let customHeader: [String: String]?
    private let contentMode: SwiftUI.ContentMode
    private let usesCrossfade: Bool
    private let onProgress: ((Double?) -> Void)?
    private var downsamplingEnabled = true
    @Environment(\.displayScale) private var displayScale
    @State private var measuredPixels: CGSize = .zero
    @State private var requestedPixels: CGSize = .zero
    @State private var previousImage: (url: URL?, image: KFCrossPlatformImage)?

    private init(
        data: URL?,
        placeholder: URL?,
        customHeader: [String: String]?,
        contentMode: SwiftUI.ContentMode,
        usesCrossfade: Bool = false,
        onProgress: ((Double?) -> Void)? = nil
    ) {
        self.data = data
        self.placeholder = placeholder
        self.customHeader = customHeader
        self.contentMode = contentMode
        self.usesCrossfade = usesCrossfade
        self.onProgress = onProgress
    }

    public var body: some View {
        NetworkImageBoundsLayout {
            if data?.pathExtension.lowercased() == "gif" {
                KFAnimatedImage(data)
                    .configure { view in
                        view.needsPrescaling = downsamplingEnabled
                        #if os(iOS)
                        view.contentMode = contentMode == .fill ? .scaleAspectFill : .scaleAspectFit
                        #endif
                    }
                    .flareAsyncCacheTypeCheck()
                    .loadDiskFileSynchronously(false)
                    .fade(duration: 0.25)
                    .requestModifier { request in
                        customHeader?.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
                    }
                    .onProgress { receivedSize, totalSize in
                        reportProgress(receivedSize: receivedSize, totalSize: totalSize)
                    }
                    .onSuccess { _ in onProgress?(1) }
                    .onFailure { _ in onProgress?(nil) }
                    .placeholder { placeholderView }
                    .cancelOnDisappear(true)
                    .aspectRatio(contentMode: contentMode)
            } else if downsamplingEnabled && (requestedPixels.width <= 0 || requestedPixels.height <= 0) {
                placeholderView
            } else if usesCrossfade {
                CrossfadeNetworkImage(
                    data: data, placeholder: placeholder, customHeader: customHeader,
                    contentMode: contentMode, onProgress: onProgress,
                    processor: processor, displayScale: displayScale
                )
            } else {
                KFImage(data)
                    .setProcessor(processor)
                    .scaleFactor(displayScale)
                    .cacheOriginalImage()
                    .flareAsyncCacheTypeCheck()
                    .flareBackgroundDecodeIfSupported()
                    .loadDiskFileSynchronously(false)
                    .resizable()
                    .fade(duration: 0.25)
                    .requestModifier { request in
                        customHeader?.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
                    }
                    .onProgress { receivedSize, totalSize in
                        reportProgress(receivedSize: receivedSize, totalSize: totalSize)
                    }
                    .onSuccess { result in
                        previousImage = (data, result.image)
                        onProgress?(1)
                    }
                    .onFailure { _ in onProgress?(nil) }
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
            previousImage = nil
        }
    }

    private var processor: any ImageProcessor {
        downsamplingEnabled
            ? ImageDownsamplingProcessor(pixelSize: requestedPixels, fillsBounds: contentMode == .fill)
            : DefaultImageProcessor.default
    }

    @ViewBuilder
    private var placeholderView: some View {
        if let previousImage, previousImage.url == data {
            #if os(macOS)
            Image(nsImage: previousImage.image).resizable()
            #else
            Image(uiImage: previousImage.image).resizable()
            #endif
        } else if let placeholder {
            NetworkImage(data: placeholder, customHeader: customHeader, contentMode: contentMode)
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
    let data: URL?
    let placeholder: URL?
    let customHeader: [String: String]?
    let contentMode: SwiftUI.ContentMode
    let onProgress: ((Double?) -> Void)?
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

            KFImage(data)
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
                    loadedURL = data
                    loadedImage = result.image
                    loadedProcessorID = processor.identifier
                    keepsPlaceholder = true
                    withAnimation(.easeInOut(duration: transitionDuration)) {
                        showsLoadedImage = true
                    }
                    schedulePlaceholderRemoval(for: data)
                }
                .onFailure { _ in
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
        } else if let placeholder {
            NetworkImage(
                data: placeholder,
                customHeader: customHeader,
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

    init(
        data: String?,
        customHeader: [String: String]? = nil,
        contentMode: SwiftUI.ContentMode = .fill,
        usesCrossfade: Bool = false,
        onProgress: ((Double?) -> Void)? = nil
    ) {
        self.init(
            data: data.flatMap(URL.init(string:)),
            placeholder: nil,
            customHeader: customHeader,
            contentMode: contentMode,
            usesCrossfade: usesCrossfade,
            onProgress: onProgress
        )
    }
    init(
        data: String,
        customHeader: [String: String]? = nil,
        contentMode: SwiftUI.ContentMode = .fill,
        usesCrossfade: Bool = false,
        onProgress: ((Double?) -> Void)? = nil
    ) {
        self.init(
            data: .init(string: data),
            placeholder: nil,
            customHeader: customHeader,
            contentMode: contentMode,
            usesCrossfade: usesCrossfade,
            onProgress: onProgress
        )
    }
    init(
        data: String,
        placeholder: String,
        customHeader: [String: String]? = nil,
        contentMode: SwiftUI.ContentMode = .fill,
        usesCrossfade: Bool = false,
        onProgress: ((Double?) -> Void)? = nil
    ) {
        self.init(
            data: .init(string: data),
            placeholder: .init(string: placeholder),
            customHeader: customHeader,
            contentMode: contentMode,
            usesCrossfade: usesCrossfade,
            onProgress: onProgress
        )
    }
    init(
        data: URL?,
        contentMode: SwiftUI.ContentMode = .fill,
        usesCrossfade: Bool = false,
        onProgress: ((Double?) -> Void)? = nil
    ) {
        self.init(
            data: data,
            placeholder: nil,
            customHeader: nil,
            contentMode: contentMode,
            usesCrossfade: usesCrossfade,
            onProgress: onProgress
        )
    }
    init(
        data: URL?,
        customHeader: [String: String]?,
        contentMode: SwiftUI.ContentMode = .fill,
        usesCrossfade: Bool = false,
        onProgress: ((Double?) -> Void)? = nil
    ) {
        self.init(
            data: data,
            placeholder: nil,
            customHeader: customHeader,
            contentMode: contentMode,
            usesCrossfade: usesCrossfade,
            onProgress: onProgress
        )
    }
}
