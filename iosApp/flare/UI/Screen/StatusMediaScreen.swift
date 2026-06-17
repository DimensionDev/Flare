import SwiftUI
import KotlinSharedUI
import LazyPager
import AVKit
import Photos
import Kingfisher
import SwiftUIBackports
import VideoPlayer
import Combine
import UIKit

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
    @State private var shareFileURL: URL?
    @State private var shareFileSourceURL: String?
    @State private var holdsPlaybackSession: Bool = false
    @State private var playbackRate: Float = 1
    @State private var isLandscapeViewing: Bool = false

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
                .onDismiss(backgroundOpacity: $opacity) {
                    dismiss()
                }
                .onTap {
                    withAnimation {
                        showData = !showData
                    }
                }
                .onDoubleTap {}
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
                    if shouldShowStatusOverlay {
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
            await loadShareFile(url: selectedImageURL, customHeaders: selectedImageCustomHeaders)
        }
        .onChange(of: selectedIndex) { oldValue, newValue in
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
                            Image("fa-download")
                        }
                    }
                    ToolbarItem(placement: .primaryAction) {
                        if let shareFileURL, shareFileSourceURL == selectedMedia.url {
                            ShareLink(item: shareFileURL) {
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
                } else if let selectedMedia, case .video(let video) = onEnum(of: selectedMedia) {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            MediaSaver.shared.saveVideo(url: video.url, customHeaders: video.customHeaders)
                        } label: {
                            Image("fa-download")
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

    private var selectedMediaIsVideo: Bool {
        guard let selectedMedia else { return false }
        return isVideoMedia(selectedMedia)
    }

    private var shouldShowStatusOverlay: Bool {
        if selectedMediaIsVideo {
            return showData || playbackRate > 1
        }
        return showData && !isLandscapeViewing
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
                statusKey: statusKey.description(),
                userHandle: statusUserHandle
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

    private var statusUserHandle: String {
        if case .success(let success) = onEnum(of: presenter.state.status),
           let content = success.data as? UiTimelineV2.Post {
            return content.user?.handle.canonical ?? "unknown"
        }
        return "unknown"
    }
    
    var statusView: some View {
        VStack(
            spacing: 8,
        ) {
            if showData, !isLandscapeViewing, medias.count > 1 {
                LazyPagerIndicator(count: medias.count, page: $selectedIndex)
            }
            
            if let selectedMedia {
                if case .video = onEnum(of: selectedMedia) {
                    VideoControlView(
                        isPlaying: $isPlaying,
                        currentTime: $currentTime,
                        videoState: videoState,
                        playbackRate: playbackRate
                    )
                }
            }
            
            if showData, !isLandscapeViewing {
                StateView(state: presenter.state.status) { timeline in
                    if let content = timeline as? UiTimelineV2.Post {
                        StatusView(data: content, isQuote: true, showMedia: false, maxLine: 3, showExpandTextButton: false, showParents: false)
                    }
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
    let playbackRate: Float
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
            if playbackRate > 1 {
                HStack(spacing: 6) {
                    Image(systemName: "forward.fill")
                    Text(verbatim: "\(formatRate(playbackRate))x")
                }
                .font(.caption.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(.black.opacity(0.65), in: .capsule)
                .transition(.opacity.combined(with: .scale(scale: 0.96)))
            }

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
        .onChange(of: playbackRate) { _, _ in
            baselineSeconds = sliderValue
            baselineDate = Date()
        }
        .onChange(of: duration) { _, newValue in
            if sliderValue > newValue {
                sliderValue = newValue
            }
        }
        .onReceive(progressTimer) { _ in
            guard !isSeeking, isPlaying, duration > 0 else { return }
            let elapsed = Date().timeIntervalSince(baselineDate)
            let projected = min(baselineSeconds + elapsed * Double(playbackRate), duration)
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

    func formatRate(_ rate: Float) -> String {
        if rate.rounded() == rate {
            return String(format: "%.0f", rate)
        }
        return String(format: "%.1f", rate)
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
    @Binding var playbackRate: Float
    @State private var isAppeared: Bool = false
    @State private var playbackResumeTask: Task<Void, Never>?
    @State private var fastPlaybackWasPlaying: Bool = false
    @State private var isFastPlaybackActive: Bool = false
    @State private var seekFeedback: SeekFeedback?
    @State private var seekFeedbackOpacity: Double = 0
    @State private var seekFeedbackTask: Task<Void, Never>?
    let data: UiMediaVideo
    private let seekInterval: Double = 5
    private let normalPlaybackRate: Float = 1
    private let fastPlaybackRate: Float = 2
    
    init(
        data: UiMediaVideo,
        play: Binding<Bool>,
        videoState: Binding<VideoState>,
        time: Binding<CMTime>,
        playbackRate: Binding<Float>
    ) {
        self.data = data
        self._play = play
        self._videoState = videoState
        self._time = time
        self._playbackRate = playbackRate
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
                    .speedRate(playbackRate)
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
        .overlay {
            VideoGestureOverlay(
                onDoubleTap: { x, width in
                    if x < width / 2 {
                        seek(by: -seekInterval)
                        showSeekFeedback(.backward)
                    } else {
                        seek(by: seekInterval)
                        showSeekFeedback(.forward)
                    }
                },
                onLongPressChanged: { pressing in
                    if pressing {
                        beginFastPlayback()
                    } else {
                        endFastPlayback()
                    }
                }
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityHidden(true)
        }
        .overlay {
            if let seekFeedback {
                HStack {
                    if seekFeedback == .forward {
                        Spacer()
                    }
                    Image(systemName: seekFeedback.iconName)
                        .font(.system(size: 44, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(20)
                        .background(.black.opacity(0.55), in: .circle)
                    if seekFeedback == .backward {
                        Spacer()
                    }
                }
                .padding(.horizontal, 56)
                .opacity(seekFeedbackOpacity)
            }
        }
        .onDisappear {
            endFastPlayback()
            seekFeedbackTask?.cancel()
        }
    }

    private func seek(by offset: Double) {
        let currentSeconds = time.seconds.isFinite ? time.seconds : 0
        let target: Double
        if let duration {
            target = min(max(currentSeconds + offset, 0), duration)
        } else {
            target = max(currentSeconds + offset, 0)
        }
        time = CMTime(seconds: target, preferredTimescale: 600)
    }

    private func showSeekFeedback(_ feedback: SeekFeedback) {
        seekFeedbackTask?.cancel()
        seekFeedback = feedback
        seekFeedbackOpacity = 0
        withAnimation(.easeOut(duration: 0.12)) {
            seekFeedbackOpacity = 1
        }
        seekFeedbackTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 260_000_000)
            guard !Task.isCancelled else { return }
            withAnimation(.easeIn(duration: 0.22)) {
                seekFeedbackOpacity = 0
            }
            try? await Task.sleep(nanoseconds: 240_000_000)
            guard !Task.isCancelled else { return }
            seekFeedback = nil
        }
    }

    private var duration: Double? {
        switch videoState {
        case .playing(let duration), .paused(let duration):
            return duration > 0 ? duration : nil
        default:
            return nil
        }
    }

    private func beginFastPlayback() {
        guard !isFastPlaybackActive else { return }
        fastPlaybackWasPlaying = play
        playbackResumeTask?.cancel()
        play = false
        playbackRate = fastPlaybackRate
        isFastPlaybackActive = true
        playbackResumeTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 16_000_000)
            guard !Task.isCancelled else { return }
            play = true
        }
    }

    private func endFastPlayback() {
        playbackResumeTask?.cancel()
        playbackResumeTask = nil
        guard isFastPlaybackActive else { return }
        let shouldResume = fastPlaybackWasPlaying
        play = false
        playbackRate = normalPlaybackRate
        if shouldResume {
            playbackResumeTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 16_000_000)
                guard !Task.isCancelled else { return }
                play = true
            }
        }
        isFastPlaybackActive = false
    }
}

private enum SeekFeedback {
    case backward
    case forward

    var iconName: String {
        switch self {
        case .backward:
            return "gobackward.5"
        case .forward:
            return "goforward.5"
        }
    }
}

private struct VideoGestureOverlay: UIViewRepresentable {
    let onDoubleTap: (CGFloat, CGFloat) -> Void
    let onLongPressChanged: (Bool) -> Void

    func makeUIView(context: Context) -> UIView {
        let view = WindowGestureHostView()
        view.backgroundColor = .clear
        view.onWindowChanged = { [weak coordinator = context.coordinator, weak view] in
            coordinator?.installGestures(from: view)
        }
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onDoubleTap = onDoubleTap
        context.coordinator.onLongPressChanged = onLongPressChanged
        DispatchQueue.main.async {
            context.coordinator.installGestures(from: uiView)
        }
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.uninstallGestures()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onDoubleTap: onDoubleTap, onLongPressChanged: onLongPressChanged)
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onDoubleTap: (CGFloat, CGFloat) -> Void
        var onLongPressChanged: (Bool) -> Void
        private weak var sourceView: UIView?
        private weak var installedWindow: UIWindow?
        private var doubleTapRecognizer: UITapGestureRecognizer?
        private var longPressRecognizer: UILongPressGestureRecognizer?
        private var longPressBeganInside = false

        init(
            onDoubleTap: @escaping (CGFloat, CGFloat) -> Void,
            onLongPressChanged: @escaping (Bool) -> Void
        ) {
            self.onDoubleTap = onDoubleTap
            self.onLongPressChanged = onLongPressChanged
        }

        func installGestures(from view: UIView?) {
            sourceView = view
            guard let window = view?.window, installedWindow !== window else { return }
            uninstallGestures()
            sourceView = view

            let doubleTap = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTap(_:)))
            doubleTap.numberOfTapsRequired = 2
            doubleTap.cancelsTouchesInView = false
            doubleTap.delegate = self

            let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
            longPress.minimumPressDuration = 0.35
            longPress.allowableMovement = 80
            longPress.cancelsTouchesInView = false
            longPress.delegate = self

            window.addGestureRecognizer(doubleTap)
            window.addGestureRecognizer(longPress)
            installedWindow = window
            doubleTapRecognizer = doubleTap
            longPressRecognizer = longPress
        }

        func uninstallGestures() {
            if let doubleTapRecognizer {
                installedWindow?.removeGestureRecognizer(doubleTapRecognizer)
            }
            if let longPressRecognizer {
                installedWindow?.removeGestureRecognizer(longPressRecognizer)
            }
            installedWindow = nil
            doubleTapRecognizer = nil
            longPressRecognizer = nil
            longPressBeganInside = false
        }

        @objc func handleDoubleTap(_ recognizer: UITapGestureRecognizer) {
            guard recognizer.state == .ended,
                  let window = recognizer.view,
                  let sourceView,
                  let location = localLocation(from: recognizer, in: window, sourceView: sourceView) else { return }
            onDoubleTap(location.x, sourceView.bounds.width)
        }

        @objc func handleLongPress(_ recognizer: UILongPressGestureRecognizer) {
            guard let window = recognizer.view,
                  let sourceView else { return }

            switch recognizer.state {
            case .began:
                longPressBeganInside = localLocation(from: recognizer, in: window, sourceView: sourceView) != nil
                if longPressBeganInside {
                    onLongPressChanged(true)
                }
            case .ended, .cancelled, .failed:
                if longPressBeganInside {
                    onLongPressChanged(false)
                }
                longPressBeganInside = false
            default:
                break
            }
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            guard let sourceView,
                  let window = installedWindow else { return false }
            let point = touch.location(in: window)
            return sourceView.convert(sourceView.bounds, to: window).contains(point)
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        private func localLocation(
            from recognizer: UIGestureRecognizer,
            in windowView: UIView,
            sourceView: UIView
        ) -> CGPoint? {
            let pointInWindow = recognizer.location(in: windowView)
            let sourceFrame = sourceView.convert(sourceView.bounds, to: windowView)
            guard sourceFrame.contains(pointInWindow) else { return nil }
            return sourceView.convert(pointInWindow, from: windowView)
        }
    }
}

private final class WindowGestureHostView: UIView {
    var onWindowChanged: (() -> Void)?

    override func didMoveToWindow() {
        super.didMoveToWindow()
        onWindowChanged?()
    }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        false
    }
}

@MainActor
enum MediaOrientationController {
    static func setLandscape(_ enabled: Bool) {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }) else {
            return
        }

        scene.mediaKeyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        let orientations: UIInterfaceOrientationMask = enabled ? .landscapeRight : .portrait
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: orientations)) { error in
            print("Media orientation request failed: \(error)")
        }
    }
}

private extension UIWindowScene {
    var mediaKeyWindow: UIWindow? {
        windows.first { $0.isKeyWindow }
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
