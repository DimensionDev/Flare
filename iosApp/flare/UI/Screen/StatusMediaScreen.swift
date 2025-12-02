import SwiftUI
import KotlinSharedUI
import LazyPager
import AVKit
import Photos
import Kingfisher
import SwiftUIBackports
import VideoPlayer

struct StatusMediaScreen: View {
    @Environment(\.appearanceSettings) private var appearanceSettings
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
    @State var canShow = false
    var body: some View {
        ZStack {
            if canShow {
                if medias.isEmpty {
                    if let preview {
                        AdaptiveKFImage(data: preview, placeholder: nil)
                    } else {
                        ProgressView()
                    }
                } else {
                    LazyPager(data: medias, page: $selectedIndex) { media in
                        switch onEnum(of: media) {
                        case .image(let image):
                            AdaptiveKFImage(data: image.url, placeholder: image.previewUrl)
                        case .video(let video):
                            if selectedIndex == medias.firstIndex(where: { $0.url == video.url }) {
                                StatusMediaVideoView(data: video, play: $isPlaying, videoState: $videoState, time: $currentTime)
                            } else {
                                NetworkImage(data: video.thumbnailUrl)
                                    .scaledToFit()
                            }
                        case .gif(let gif):
                            NetworkImage(data: gif.url, placeholder: gif.previewUrl)
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
                    .zoomable { item in
                        if case .video = onEnum(of: item) {
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
            } else {
                Color.clear
                    .onAppear {
                        selectedIndex = initialIndex
                        canShow = true
                    }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onChange(of: selectedIndex) { oldValue, newValue in
            isPlaying = true
            videoState = .idle
            currentTime = .zero
        }
        .onChange(of: presenter.state.status) { oldValue, newValue in
            if medias.isEmpty, case .success(let success) = onEnum(of: newValue), case .status(let content) = onEnum(of: success.data.content) {
                withAnimation {
                    medias = content.images
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
                let selectedMedia = medias[selectedIndex]
                if case .image = onEnum(of: selectedMedia) {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            MediaSaver.shared.saveImage(url: selectedMedia.url)
                        } label: {
                            Image("fa-download")
                        }
                    }
                }
            }
        }
    }
    
    var statusView: some View {
        VStack(
            spacing: 8,
        ) {
            if medias.count > 1 {
                LazyPagerIndicator(count: medias.count, page: $selectedIndex)
            }
            
            if !medias.isEmpty {
                let selectedMedia = medias[selectedIndex]
                if case .video = onEnum(of: selectedMedia) {
                    VideoControlView(isPlaying: $isPlaying, currentTime: $currentTime, videoState: videoState)
                }
            }
            
            StateView(state: presenter.state.status) { timeline in
                if case .status(let content) = onEnum(of: timeline.content) {
                    StatusView(data: content, isQuote: true, showMedia: false, maxLine: 3, showExpandTextButton: false)
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
                        .contentTransition(.symbolEffect(.replace))
                }
                .buttonStyle(.plain)
                
                Text(formatTime(currentTime.seconds))
                    .font(.caption)
                    .monospacedDigit()
                
                Slider(value: Binding(get: {
                    currentTime.seconds
                }, set: { newValue in
                    currentTime = CMTime(seconds: newValue, preferredTimescale: 600)
                }), in: 0...max(duration, 0.1))
                
                Text(formatTime(duration))
                    .font(.caption)
                    .monospacedDigit()
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
                NetworkImage(data: data.thumbnailUrl)
                    .scaledToFit()
                    .allowsHitTesting(false)
            }
            .clipped()
        .overlay {
            VideoPlayer(url: .init(string: data.url)!, play: $play, time: $time)
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

struct AdaptiveKFImage: View {
    let data: String
    let placeholder: String?

    @State private var shouldFill = false
    private let wideThreshold: CGFloat = 19.5 / 9.0

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
        KFImage(.init(string: data))
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
                    AdaptiveKFImage(data: placeholder, placeholder: nil)
                } else {
                    ProgressView()
                }
            }
            .resizable()
            .aspectRatio(contentMode: shouldFill ? .fill : .fit)
    }
}
