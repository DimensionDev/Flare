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
    @State private var showData = true
    @State private var protectInitialPagerSelection: Bool
    @State private var didApplyInitialSelection = false
    @State private var shareFileURL: URL?
    @State private var shareFileSourceURL: String?
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
            if medias.isEmpty {
                if let preview {
                    AdaptiveKFImage(data: preview, placeholder: nil)
                } else {
                    ProgressView()
                }
            } else {
                LazyPager(data: medias, page: pagerSelectedIndex) { media in
                    mediaContent(media)
                }
                .onDismiss(backgroundOpacity: $opacity) {
                    dismiss()
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
                .overlay(alignment: .bottom) {
                    if shouldShowBottomOverlay {
                        bottomOverlay
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task(id: selectedShareFileIdentity) {
            await loadShareFile(url: selectedImageURL, customHeaders: selectedImageCustomHeaders)
        }
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
        .ignoresSafeArea()
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Image(fontAwesome: .xmark)
                }
            }
            if !medias.isEmpty {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            isLandscapeViewing.toggle()
                        }
                    } label: {
                        Image(systemName: isLandscapeViewing ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                    }
                    .accessibilityLabel(Text(verbatim: isLandscapeViewing ? "Exit landscape view" : "Landscape view"))
                }
                if let selectedMedia, case .image = onEnum(of: selectedMedia) {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            MediaSaver.shared.saveImage(url: selectedMedia.url, customHeaders: selectedMedia.customHeaders)
                        } label: {
                            Image(fontAwesome: .download)
                        }
                    }
                    ToolbarItem(placement: .primaryAction) {
                        if let shareFileURL, shareFileSourceURL == selectedMedia.url {
                            ShareLink(item: shareFileURL) {
                                Image(fontAwesome: .shareNodes)
                            }
                            .accessibilityLabel("Share image")
                        } else {
                            Button {
                            } label: {
                                Image(fontAwesome: .shareNodes)
                            }
                            .disabled(true)
                            .accessibilityLabel("Share image")
                        }
                    }
                } else if let selectedMedia, case .video(let video) = onEnum(of: selectedMedia) {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            MediaSaver.shared.saveVideo(url: video.url, customHeaders: video.customHeaders)
                        } label: {
                            Image(fontAwesome: .download)
                        }
                    }
                }
            }
        }
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

    private var selectedImageURL: String? {
        guard let selectedMedia else {
            return nil
        }

        switch onEnum(of: selectedMedia) {
        case .image(let image):
            return image.url
        case .video, .gif, .audio:
            return nil
        }
    }

    private var selectedImageCustomHeaders: [String: String]? {
        selectedMedia?.customHeaders
    }

    private var selectedShareFileIdentity: String {
        [
            selectedImageURL,
            shareContext?.statusKey,
            shareContext?.userHandle,
        ].compactMap { $0 }.joined(separator: "|")
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
            return showData || playbackRate > 1
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

    private func loadShareFile(url: String?, customHeaders: [String: String]?) async {
        shareFileURL = nil
        shareFileSourceURL = url

        guard let url else {
            return
        }

        do {
            let fileURL = try await OriginalImageShareFile.make(
                url: url,
                customHeaders: customHeaders,
                statusKey: shareContext?.statusKey,
                userHandle: shareContext?.userHandle
            )
            guard !Task.isCancelled, shareFileSourceURL == url else {
                return
            }
            shareFileURL = fileURL
        } catch {
            guard !Task.isCancelled, shareFileSourceURL == url else {
                return
            }
            shareFileURL = nil
        }
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
