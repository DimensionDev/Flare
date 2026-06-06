import SwiftUI
import KotlinSharedUI
import LazyPager
import AVKit
import Photos
import Kingfisher
import SwiftUIBackports
import VideoPlayer
import Combine

struct StatusMediaScreen: View {
    @Environment(\.dismiss) var dismiss
    let accountType: AccountType
    let statusKey: MicroBlogKey
    let initialIndex: Int
    let preview: String?
    @StateObject private var presenter: KotlinPresenter<StatusState>
    @State private var medias: [any UiMedia] = []
    @State private var selectedIndex: Int = 0
    @State private var isPlaying: Bool = true
    @State private var videoState: VideoState = .idle
    @State private var currentTime: CMTime = .zero
    @State var opacity: CGFloat = 1 // Dismiss gesture background opacity
    @State var showData = true
    @State private var protectInitialPagerSelection: Bool = false
    @State private var shareImage: UIImage?
    @State private var shareImageURL: String?
    @State private var holdsPlaybackSession: Bool = false

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
                    switch onEnum(of: media) {
                    case .image(let image):
                        AdaptiveKFImage(data: image.url, placeholder: image.previewUrl, customHeader: image.customHeaders)
                    case .video(let video):
                        if selectedIndex == medias.firstIndex(where: { $0.url == video.url }) {
                            StatusMediaVideoView(data: video, play: $isPlaying, videoState: $videoState, time: $currentTime)
                        } else {
                            NetworkImage(data: video.thumbnailUrl, customHeader: video.customHeaders)
                                .scaledToFit()
                        }
                    case .gif(let gif):
                        NetworkImage(data: gif.url, placeholder: gif.previewUrl, customHeader: gif.customHeaders)
                            .scaledToFit()
                    case .audio(let audio):
                        EmptyView()
                    }
                }
                .onDismiss(backgroundOpacity: $opacity) {
                    dismiss()
                }
                .onTap {
                    withAnimation {
                        showData = !showData
                    }
                }
                .onDrag {
                    protectInitialPagerSelection = false
                }
                .zoomable { item in
                    if isVideoMedia(item) {
                        return .disabled
                    } else {
                        return .custom(min: 1, max: 5, doubleTap: .scale(2))
                    }
                }
                .settings { config in
                    config.preloadAmount = 99
                }
                .overlay(alignment: .bottom) {
                    if showData {
                        if #available(iOS 26.0, *) {
                            statusView
                                .padding()
                                .backport
                                .glassEffect(.tinted(.init(.systemGroupedBackground).opacity(0.5)), in: .rect(corners: .concentric, isUniform: true), fallbackBackground: .regularMaterial)
                                .padding()
                                .transition(.move(edge: .bottom).combined(with: .opacity))
                        } else {
                            statusView
                                .padding()
                                .safeAreaPadding(.bottom)
                                .backport
                                .glassEffect(.regular, in: .rect(), fallbackBackground: .regularMaterial)
                                .transition(.move(edge: .bottom).combined(with: .opacity))
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task(id: selectedImageURL) {
            await loadShareImage(url: selectedImageURL, customHeaders: selectedImageCustomHeaders)
        }
        .onChange(of: selectedIndex) { oldValue, newValue in
            isPlaying = true
            videoState = .idle
            currentTime = .zero
        }
        .onChange(of: isVideoActivelyPlaying) { _, newValue in
            updatePlaybackSession(playing: newValue)
        }
        .onDisappear {
            if holdsPlaybackSession {
                AudioSessionManager.shared.endPlayback()
                holdsPlaybackSession = false
            }
        }
        .onChange(of: presenter.state.status) { oldValue, newValue in
            if medias.isEmpty,
               case .success(let success) = onEnum(of: newValue),
               let content = success.data as? UiTimelineV2.Post {
                let contentMedias = Array(content.images)
                let initialSelection = clampedIndex(initialIndex, count: contentMedias.count)
                selectedIndex = initialSelection
                protectInitialPagerSelection = initialSelection > 0
                withAnimation {
                    medias = contentMedias
                }
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
                    Image("fa-xmark")
                }
            }
            if !medias.isEmpty {
                if let selectedMedia, case .image = onEnum(of: selectedMedia) {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            MediaSaver.shared.saveImage(url: selectedMedia.url, customHeaders: selectedMedia.customHeaders)
                        } label: {
                            Image("fa-download")
                        }
                    }
                    ToolbarItem(placement: .primaryAction) {
                        if let shareImage, shareImageURL == selectedMedia.url {
                            ShareLink(
                                item: Image(uiImage: shareImage),
                                preview: SharePreview("Share image", image: Image(uiImage: shareImage))
                            ) {
                                Image("fa-share-nodes")
                            }
                            .accessibilityLabel("Share image")
                        } else {
                            Button {
                            } label: {
                                Image("fa-share-nodes")
                            }
                            .disabled(true)
                            .accessibilityLabel("Share image")
                        }
                    }
                }
            }
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
        guard let selectedMedia else {
            return nil
        }
        return selectedMedia.customHeaders
    }

    private var isVideoActivelyPlaying: Bool {
        if case .playing = videoState { return true }
        return false
    }

    private func isVideoMedia(_ media: any UiMedia) -> Bool {
        if case .video = onEnum(of: media) {
            return true
        }
        return false
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

    private func loadShareImage(url: String?, customHeaders: [String: String]?) async {
        shareImage = nil
        shareImageURL = url

        guard let url, let imageURL = URL(string: url) else {
            return
        }

        do {
            let result = try await KingfisherManager.shared.retrieveImage(with: imageURL, options: kingfisherOptions(customHeaders: customHeaders))
            guard !Task.isCancelled, shareImageURL == url else {
                return
            }
            shareImage = result.image
        } catch {
            guard !Task.isCancelled, shareImageURL == url else {
                return
            }
            shareImage = nil
        }
    }

    private func kingfisherOptions(customHeaders: [String: String]?) -> KingfisherOptionsInfo {
        guard let customHeaders, !customHeaders.isEmpty else {
            return []
        }
        return [.requestModifier(AnyModifier { request in
            var request = request
            for (key, value) in customHeaders {
                request.setValue(value, forHTTPHeaderField: key)
            }
            return request
        })]
    }
    
    var statusView: some View {
        VStack(
            spacing: 8,
        ) {
            if medias.count > 1 {
                LazyPagerIndicator(count: medias.count, page: $selectedIndex)
            }
            
            if let selectedMedia {
                if case .video = onEnum(of: selectedMedia) {
                    VideoControlView(isPlaying: $isPlaying, currentTime: $currentTime, videoState: videoState)
                }
            }
            
            StateView(state: presenter.state.status) { timeline in
                if let content = timeline as? UiTimelineV2.Post {
                    StatusView(data: content, isQuote: true, showMedia: false, maxLine: 3, showExpandTextButton: false, showParents: false)
                }
            }
        }
    }
}

struct LazyPagerIndicator: View {
    let count: Int
    @Binding var page: Int
    
    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<count, id: \.self) { index in
                Circle()
                    .fill(index == page ? Color.accentColor : Color.secondary)
                    .frame(width: 8, height: 8)
            }
        }
    }
}

struct VideoControlView: View {
    @Binding var isPlaying: Bool
    @Binding var currentTime: CMTime
    let videoState: VideoState
    @State private var sliderValue: Double = 0
    @State private var isSeeking = false
    @State private var wasPlayingBeforeSeek = false
    @State private var baselineSeconds: Double = 0
    @State private var baselineDate: Date = Date()

    private let progressTimer = Timer.publish(every: 0.1, on: .main, in: .common).autoconnect()

    var duration: Double {
        switch videoState {
        case .playing(let d), .paused(let d): return d
        default: return 0
        }
    }
    
    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Button {
                    isPlaying.toggle()
                } label: {
                    Image(isPlaying ? "fa-pause" : "fa-play")
                        .font(.title2)
                        .frame(height: 24)
                        .contentTransition(.symbolEffect(.replace))
                }
                .contentShape(Rectangle())
                .backport
                .glassButtonStyle(fallbackStyle: .plain)
                
                Text(formatTime(sliderValue))
                    .font(.caption)
                    .monospacedDigit()
                
                Slider(value: $sliderValue, in: 0...max(duration, 0.1)) { editing in
                    isSeeking = editing
                    if editing {
                        wasPlayingBeforeSeek = isPlaying
                        isPlaying = false
                    } else {
                        currentTime = CMTime(seconds: sliderValue, preferredTimescale: 600)
                        baselineSeconds = sliderValue
                        baselineDate = Date()
                        if wasPlayingBeforeSeek {
                            isPlaying = true
                        }
                    }
                }
                
                Text(formatTime(duration))
                    .font(.caption)
                    .monospacedDigit()
            }
        }
        .onAppear {
            let seconds = currentTime.seconds.isFinite ? currentTime.seconds : 0
            sliderValue = seconds
            baselineSeconds = seconds
            baselineDate = Date()
        }
        .onChange(of: currentTime.seconds) { _, newValue in
            guard !isSeeking, newValue.isFinite else { return }
            baselineSeconds = newValue
            baselineDate = Date()
            sliderValue = newValue
        }
        .onChange(of: isPlaying) { _, playing in
            if playing {
                baselineSeconds = sliderValue
                baselineDate = Date()
            }
        }
        .onChange(of: duration) { _, newValue in
            if sliderValue > newValue {
                sliderValue = newValue
            }
        }
        .onReceive(progressTimer) { _ in
            guard !isSeeking, isPlaying, duration > 0 else { return }
            let elapsed = Date().timeIntervalSince(baselineDate)
            let projected = min(baselineSeconds + elapsed, duration)
            if projected != sliderValue {
                sliderValue = projected
            }
        }
    }
    
    func formatTime(_ seconds: Double) -> String {
        if seconds.isNaN || seconds.isInfinite {
            return "0:00"
        }
        let seconds = Int(seconds)
        let m = seconds / 60
        let s = seconds % 60
        return String(format: "%d:%02d", m, s)
    }
}

extension StatusMediaScreen {
    init(
        accountType: AccountType,
        statusKey: MicroBlogKey,
        initialIndex: Int,
        preview: String?
    ) {
        self.accountType = accountType
        self.statusKey = statusKey
        self.initialIndex = initialIndex
        self.preview = preview
        self._selectedIndex = .init(initialValue: max(0, initialIndex))
        self._protectInitialPagerSelection = .init(initialValue: initialIndex > 0)
        self._presenter = .init(wrappedValue: .init(presenter: StatusPresenter(accountType: accountType, statusKey: statusKey)))
    }
}

enum VideoState {
    case idle
    case loading
    case playing(Double)
    case paused(Double)
    case error(Error)
}

struct StatusMediaVideoView: View {
    @Binding var play: Bool
    @Binding var videoState: VideoState
    @Binding var time: CMTime
    @State private var isAppeared: Bool = false
    let data: UiMediaVideo
    
    init(data: UiMediaVideo, play: Binding<Bool>, videoState: Binding<VideoState>, time: Binding<CMTime>) {
        self.data = data
        self._play = play
        self._videoState = videoState
        self._time = time
    }
    
    var body: some View {
        Color.clear
//            .opacity(0.2)
            .overlay {
                if case .idle = videoState {
                    NetworkImage(data: data.thumbnailUrl, customHeader: data.customHeaders)
                        .scaledToFit()
                        .allowsHitTesting(false)
                } else {
                    EmptyView()
                }
            }
            .clipped()
        .overlay {
            if let videoURL = URL(string: data.url) {
                VideoPlayer(url: videoURL, play: $play, time: $time)
                    .mute(false)
                    .autoReplay(true)
                    .onStateChanged { state in
                        switch state {
                        case .playing(let duration): videoState = .playing(duration)
                        case .loading: videoState = .loading
                        case .paused:
                            if case .playing(let duration) = videoState {
                                videoState = .paused(duration)
                            } else if case .paused(let duration) = videoState {
                                videoState = .paused(duration)
                            } else {
                                videoState = .idle
                            }
                        case .error(let error): videoState = .error(error)
                        }
                    }
                    .contentMode(.scaleAspectFit)
                    .allowsHitTesting(false)
            }
        }
    }
}

struct AdaptiveKFImage: View {
    let data: String
    let placeholder: String?
    let customHeader: [String: String]?

    @State private var shouldFill = false
    private let wideThreshold: CGFloat = 19.5 / 9.0

    init(data: String, placeholder: String?, customHeader: [String: String]? = nil) {
        self.data = data
        self.placeholder = placeholder
        self.customHeader = customHeader
    }

    var body: some View {
        if shouldFill {
            ScrollView(.vertical, showsIndicators: false) {
                kfImageView
            }
        } else {
            kfImageView
        }
    }
    
    var kfImageView: some View {
        ZStack {
            if data.hasSuffix(".gif") {
                KFAnimatedImage(.init(string: data))
                    .requestModifier({ request in
                        if let customHeader {
                            for (key, value) in customHeader {
                                request.setValue(value, forHTTPHeaderField: key)
                            }
                        }
                    })
                    .onSuccess { result in
                        let size = result.image.size
                        let ratio = size.height / size.width
                        if ratio > wideThreshold {
                            shouldFill = true
                        } else {
                            shouldFill = false
                        }
                    }
                    .placeholder {
                        if let placeholder {
                            AdaptiveKFImage(data: placeholder, placeholder: nil, customHeader: customHeader)
                        } else {
                            ProgressView()
                        }
                    }
                    .aspectRatio(contentMode: shouldFill ? .fill : .fit)
            } else {
                KFImage(.init(string: data))
                    .requestModifier({ request in
                        if let customHeader {
                            for (key, value) in customHeader {
                                request.setValue(value, forHTTPHeaderField: key)
                            }
                        }
                    })
                    .onSuccess { result in
                        let size = result.image.size
                        let ratio = size.height / size.width
                        if ratio > wideThreshold {
                            shouldFill = true
                        } else {
                            shouldFill = false
                        }
                    }
                    .placeholder {
                        if let placeholder {
                            AdaptiveKFImage(data: placeholder, placeholder: nil, customHeader: customHeader)
                        } else {
                            ProgressView()
                        }
                    }
                    .resizable()
                    .aspectRatio(contentMode: shouldFill ? .fill : .fit)
            }
        }
    }
}
