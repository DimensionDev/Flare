import SwiftUI
import Kingfisher

public struct NetworkImage: View {
    private let data: URL?
    private let placeholder: URL?
    private let customHeader: [String: String]?
    private let contentMode: SwiftUI.ContentMode
    private let usesCrossfade: Bool
    private let onProgress: ((Double?) -> Void)?

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
        if data?.absoluteString.hasSuffix(".gif") == true {
            KFAnimatedImage(data)
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
                .onSuccess { _ in
                    onProgress?(1)
                }
                .onFailure { _ in
                    onProgress?(nil)
                }
                .placeholder {
                    if let placeholder {
                        NetworkImage(data: placeholder, customHeader: customHeader)
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
                data: data,
                placeholder: placeholder,
                customHeader: customHeader,
                contentMode: contentMode,
                onProgress: onProgress
            )
        } else {
            KFImage(data)
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
                .onSuccess { _ in
                    onProgress?(1)
                }
                .onFailure { _ in
                    onProgress?(nil)
                }
                .placeholder {
                    if let placeholder {
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
    let data: URL?
    let placeholder: URL?
    let customHeader: [String: String]?
    let contentMode: SwiftUI.ContentMode
    let onProgress: ((Double?) -> Void)?

    @State private var loadedURL: URL?
    @State private var showsLoadedImage = false
    @State private var keepsPlaceholder = true
    @State private var placeholderRemovalTask: Task<Void, Never>?
    private let transitionDuration: TimeInterval = 0.22

    var body: some View {
        ZStack {
            if keepsPlaceholder {
                placeholderView
            }

            KFImage(data)
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
                .onSuccess { _ in
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
        if let placeholder {
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

public extension NetworkImage {
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
