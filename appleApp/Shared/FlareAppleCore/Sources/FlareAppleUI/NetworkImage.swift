import SwiftUI
import Kingfisher
import KotlinSharedUI
import FlareAppleCore

public struct NetworkImage: View {
    private let data: UiMedia?
    private let placeholder: UiMedia?
    private let contentMode: SwiftUI.ContentMode
    private let usesCrossfade: Bool
    private let onProgress: ((Double?) -> Void)?
    private let onImageSize: ((CGSize) -> Void)?
    @State private var prepared: PreparedMediaImage?
    @State private var preparedIdentity: String?

    public init(data: UiMedia?, placeholder: UiMedia? = nil,
                contentMode: SwiftUI.ContentMode = .fill, usesCrossfade: Bool = false,
                onProgress: ((Double?) -> Void)? = nil, onImageSize: ((CGSize) -> Void)? = nil) {
        self.data = data
        self.placeholder = placeholder
        self.contentMode = contentMode
        self.usesCrossfade = usesCrossfade
        self.onProgress = onProgress
        self.onImageSize = onImageSize
    }

    public var body: some View {
        let identity = "\(String(describing: data?.proxy))|\(data?.cacheKey ?? "")"
        let taskIdentity = "\(identity)|\(ProxyMediaImages.shared.revision)"
        Group {
            if let prepared, preparedIdentity == identity {
                ResolvedNetworkImage(source: prepared.source, downloader: prepared.downloader, redirectHandler: prepared.redirectHandler,
                    mediaPlaceholder: placeholder, data: data.flatMap { URL(string: $0.url) },
                    placeholder: placeholder.flatMap { URL(string: $0.url) },
                    customHeader: data?.customHeaders, contentMode: contentMode,
                    usesCrossfade: usesCrossfade, onProgress: onProgress, onImageSize: onImageSize)
                    .id(prepared.key)
            } else {
                Rectangle().fill(.placeholder).redacted(reason: .placeholder)
            }
        }
        .task(id: taskIdentity) {
            guard let data else { prepared = nil; return }
            do {
                let result = try await ProxyMediaImages.shared.prepare(data)
                guard !Task.isCancelled else { return }
                if preparedIdentity != identity || prepared?.key != result.key {
                    prepared = result
                }
                preparedIdentity = identity
            } catch {
                guard !Task.isCancelled else { return }
                prepared = nil
                onProgress?(nil)
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
    let placeholder: URL?
    let customHeader: [String: String]?
    let contentMode: SwiftUI.ContentMode
    let usesCrossfade: Bool
    let onProgress: ((Double?) -> Void)?
    let onImageSize: ((CGSize) -> Void)?

    public var body: some View {
        if AppleMediaFileExtension.isGIF(url: data) {
            KFAnimatedImage(source: source)
                .downloader(downloader)
                .redirectHandler(redirectHandler)
                .flareBackgroundDecodeIfSupported()
                .loadDiskFileSynchronously(false)
                .fade(duration: 0.25)
                .requestModifier({ request in
                    if let customHeader {
                        for (key, value) in customHeader {
                            request.setValue(value, forHTTPHeaderField: key)
                        }
                    }
                })
                .onProgress { receivedSize, totalSize in
                    reportProgress(receivedSize: receivedSize, totalSize: totalSize)
                }
                .onSuccess { result in
                    onImageSize?(result.image.size)
                    onProgress?(1)
                }
                .onFailure { _ in
                    onProgress?(nil)
                }
                .placeholder {
                    if let placeholder = mediaPlaceholder {
                        NetworkImage(data: placeholder)
                    } else {
                        Rectangle()
                            .fill(.placeholder)
                            .redacted(reason: .placeholder)
                    }
                }
                .cancelOnDisappear(true)
                .aspectRatio(contentMode: contentMode)
        } else if usesCrossfade {
            CrossfadeNetworkImage(
                source: source,
                downloader: downloader,
                redirectHandler: redirectHandler,
                mediaPlaceholder: mediaPlaceholder,
                data: data,
                placeholder: placeholder,
                customHeader: customHeader,
                contentMode: contentMode,
                onProgress: onProgress,
                onImageSize: onImageSize
            )
        } else {
            KFImage(source: source)
                .downloader(downloader)
                .redirectHandler(redirectHandler)
                .flareBackgroundDecodeIfSupported()
                .loadDiskFileSynchronously(false)
                .resizable()
                .fade(duration: 0.25)
                .requestModifier({ request in
                    if let customHeader {
                        for (key, value) in customHeader {
                            request.setValue(value, forHTTPHeaderField: key)
                        }
                    }
                })
                .onProgress { receivedSize, totalSize in
                    reportProgress(receivedSize: receivedSize, totalSize: totalSize)
                }
                .onSuccess { result in
                    onImageSize?(result.image.size)
                    onProgress?(1)
                }
                .onFailure { _ in
                    onProgress?(nil)
                }
                .placeholder {
                    if let placeholder = mediaPlaceholder {
                        NetworkImage(
                            data: placeholder,
                            contentMode: contentMode
                        )
                    } else {
                        Rectangle()
                            .fill(.placeholder)
                            .redacted(reason: .placeholder)
                    }
                }
                .cancelOnDisappear(true)
                .aspectRatio(contentMode: contentMode)
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

private struct CrossfadeNetworkImage: View {
    let source: Source
    let downloader: ImageDownloader
    let redirectHandler: AnyRedirectHandler
    let mediaPlaceholder: UiMedia?
    let data: URL?
    let placeholder: URL?
    let customHeader: [String: String]?
    let contentMode: SwiftUI.ContentMode
    let onProgress: ((Double?) -> Void)?
    let onImageSize: ((CGSize) -> Void)?

    @State private var loadedURL: URL?
    @State private var showsLoadedImage = false
    @State private var keepsPlaceholder = true
    @State private var placeholderRemovalTask: Task<Void, Never>?
    let transitionDuration: TimeInterval = 0.22

    var body: some View {
        ZStack {
            if keepsPlaceholder {
                placeholderView
            }

            KFImage(source: source)
                .downloader(downloader)
                .redirectHandler(redirectHandler)
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
                    onImageSize?(result.image.size)
                    onProgress?(1)
                    loadedURL = data
                    keepsPlaceholder = true
                    withAnimation(.easeInOut(duration: transitionDuration)) {
                        showsLoadedImage = true
                    }
                    schedulePlaceholderRemoval(for: data)
                }
                .onFailure { _ in
                    onProgress?(nil)
                    loadedURL = nil
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
        if let placeholder = mediaPlaceholder {
            NetworkImage(
                data: placeholder,
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
    func flareBackgroundDecodeIfSupported() -> Self {
        #if os(macOS)
        return self
        #else
        return backgroundDecode()
        #endif
    }
}
