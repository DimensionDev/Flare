import UIKit
import AVFoundation
import Combine
import FlareAppleUI
import KotlinSharedUI

/// Owns autoplay arbitration, selection, player attachment and media handoff for
/// one list. It observes scroll state without owning or changing list position.
final class TimelineAutoplay: NSObject {
    private let collectionView: TimelineCollectionView
    private let itemID: (IndexPath) -> String?
    private let mediaSelections: TimelineMediaSelections
    private var videoAutoplay = VideoAutoplay.never
    private var networkKind = NetworkKind.cellular
    private var hasPosts = false
    private var multipleColumns = false
    private let autoplayPlayerView = VideoPlaybackSurfaceView()
    private let autoplaySession = VideoPlaybackSession()
    private var autoplayReadinessSubscription: AnyCancellable?
    private var autoplayLifecycleSubscription: AnyCancellable?
    private var autoplaySelectionTask: Task<Void, Never>?
    private var autoplayCountdownTask: Task<Void, Never>?
    private weak var currentAutoplayHostView: UIView?
    private var isAutoplayViewVisible = false
    private var isAutoplayViewportMoving = false
    private var autoplayImmediateReturn = false
    private var currentAutoplayID: String?
    private var currentAutoplayURL: URL?
    private var autoplayPolicy = TimelineAutoplayPolicy()
    private let autoplayCarousels = NSHashTable<StatusMediaUIView>.weakObjects()

    init(collectionView: TimelineCollectionView, mediaSelections: TimelineMediaSelections,
         itemID: @escaping (IndexPath) -> String?) {
        self.collectionView = collectionView
        self.mediaSelections = mediaSelections
        self.itemID = itemID
        super.init()
        setupVideoAutoplay()
    }

    func configure(videoAutoplay: VideoAutoplay, networkKind: NetworkKind, hasPosts: Bool, multipleColumns: Bool) {
        self.videoAutoplay = videoAutoplay
        self.networkKind = networkKind
        self.hasPosts = hasPosts
        self.multipleColumns = multipleColumns
        handleAutoplayAvailabilityChanged()
    }

    func setVisible(_ visible: Bool) {
        isAutoplayViewVisible = visible
        if visible {
            reconsider()
        } else {
            isAutoplayViewportMoving = false
            stop()
            VideoPlaybackArbiter.shared.withdraw(self)
        }
    }

    func scrollBegan() {
        autoplayImmediateReturn = false
        VideoPlaybackArbiter.shared.interacted(self)
        autoplayPolicy.verticalScrollBegan()
        isAutoplayViewportMoving = true
        autoplaySelectionTask?.cancel()
    }

    func didScroll() {
        if !collectionView.isScrollInteractionActive, !isAutoplayViewportMoving {
            VideoPlaybackArbiter.shared.interacted(self)
            autoplayPolicy.verticalScrollBegan()
        }
        isAutoplayViewportMoving = true
        validateCurrentAutoplayVisibility()
        reconsider()
    }

    func didEndDisplaying(_ cell: UICollectionViewCell) {
        if currentAutoplayHostView?.isDescendant(of: cell) == true { stop() }
    }

    deinit {
        autoplaySelectionTask?.cancel()
        autoplayCountdownTask?.cancel()
        NotificationCenter.default.removeObserver(self)
    }

    private func setupVideoAutoplay() {
        autoplayLifecycleSubscription = NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)
            .merge(with: NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification))
            .sink { [weak self] _ in self?.handleAutoplayAvailabilityChanged() }
        autoplayReadinessSubscription = autoplaySession.updates.sink { [weak self] in
            guard let self else { return }
            self.autoplayPlayerView.canDisplayFrame = self.autoplaySession.hasRestoredPosition
        }
        autoplayPlayerView.playerLayer.videoGravity = .resizeAspectFill
        autoplayPlayerView.isUserInteractionEnabled = false
        VideoPlaybackArbiter.shared.register(self, stop: { [weak self] in
            self?.stop()
        }, reconsider: { [weak self] in
            self?.reconsider()
        }, mediaReturned: { [weak self] urls, selected in
            self?.mediaSelections.returned(urls: urls, selectedURL: selected)
        }, resume: { [weak self] in
            self?.autoplayImmediateReturn = true
            self?.reconsider()
        }, willHandoff: { VideoPlaybackSession.continuePlayback(to: $0) })
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleTimelineVideoAutoplayNeedsUpdate),
            name: .timelineVideoAutoplayNeedsUpdate,
            object: nil
        )
    }

    @objc private func handleTimelineVideoAutoplayNeedsUpdate(_ notification: Notification) {
        guard let media = notification.object as? StatusMediaUIView,
              media.isDescendant(of: collectionView) else { return }
        autoplayCarousels.add(media)
        if notification.userInfo?["carouselInteraction"] as? Bool == true {
            autoplayImmediateReturn = false
            VideoPlaybackArbiter.shared.interacted(self)
            autoplayPolicy.interactedWithCarousel(media.autoplayGroupID)
        }
        if let url = notification.userInfo?["selectedMediaURL"] as? String {
            if notification.userInfo?["mediaClicked"] as? Bool == true {
                VideoPlaybackArbiter.shared.interacted(self)
            }
            autoplayPolicy.returnedToMedia(groupID: media.autoplayGroupID, mediaURL: url)
        }
        validateCurrentAutoplayVisibility()
        reconsider()
    }

    // MARK: - Video Autoplay

    private var isVideoAutoplayAllowed: Bool {
        guard UIApplication.shared.applicationState == .active else { return false }
        switch videoAutoplay {
        case .never:
            return false
        case .wifi:
            return networkKind == .wifi
        case .always:
            return true
        default:
            return false
        }
    }

    private func handleAutoplayAvailabilityChanged() {
        validateCurrentAutoplayVisibility()
        guard isVideoAutoplayAllowed else {
            stop()
            return
        }
        reconsider()
    }

    func reconsider(delayNanoseconds: UInt64 = 200_000_000) {
        guard !collectionView.isScrollInteractionActive else { return }
        autoplaySelectionTask?.cancel()
        guard isAutoplayViewVisible, hasPosts, isVideoAutoplayAllowed else { return }
        if autoplayImmediateReturn, !isAutoplayViewportMoving {
            selectAutoplayCandidateIfStable()
            if currentAutoplayID != nil { autoplayImmediateReturn = false }
            return
        }
        autoplaySelectionTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(nanoseconds: delayNanoseconds)
            } catch {
                return
            }
            guard let self, !Task.isCancelled else { return }
            self.selectAutoplayCandidateIfStable()
        }
    }

    private func selectAutoplayCandidateIfStable() {
        guard isAutoplayViewVisible, isVideoAutoplayAllowed, hasPosts else {
            stop()
            return
        }
        guard !collectionView.isDragging, !collectionView.isDecelerating, !collectionView.isScrollInteractionActive,
              !autoplayCarousels.allObjects.contains(where: { $0.isDescendant(of: collectionView) && $0.isCarouselScrolling }) else { return }
        isAutoplayViewportMoving = false
        guard let candidate = bestAutoplayCandidate() else {
            VideoPlaybackArbiter.shared.settledWithoutVideo(self)
            stop()
            return
        }
        playAutoplayCandidate(candidate)
    }

    private func bestAutoplayCandidate() -> TimelineVideoAutoplayCandidate? {
        let candidates = visibleAutoplayCandidates()
        let selection = candidates.compactMap { candidate -> TimelineAutoplayPolicy.Candidate? in
            guard visibleRect(for: candidate.hostView, in: collectionView) != nil else { return nil }
            let bounds = candidate.hostView.convert(candidate.hostView.bounds, to: collectionView)
            let distance = TimelineAutoplayPolicy.centerDistance(of: bounds, in: autoplayViewport, multipleColumns: multipleColumns)
            return .init(id: candidate.id, groupID: candidate.groupID, isVisible: true,
                         isSelected: candidate.isSelected, canStart: candidate.horizontalFraction >= 0.6,
                         distance: distance, mediaURL: candidate.url.absoluteString)
        }
        let id = autoplayPolicy.select(from: selection, isScrolling: false)
        return candidates.first { $0.id == id }
    }

    private func visibleAutoplayCandidates() -> [TimelineVideoAutoplayCandidate] {
        collectionView.indexPathsForVisibleItems.flatMap { indexPath -> [TimelineVideoAutoplayCandidate] in
            guard let cell = collectionView.cellForItem(at: indexPath) as? TimelineUIKitCollectionViewCell,
                  let itemID = itemID(indexPath),
                  itemID.hasPrefix("t:") else {
                return []
            }
            return cell.autoplayCandidates(prefix: itemID)
        }
    }

    private func playAutoplayCandidate(_ candidate: TimelineVideoAutoplayCandidate) {
        guard autoplaySession.player == nil || currentAutoplayID != candidate.id || currentAutoplayHostView !== candidate.hostView else {
            return
        }
        guard let newHost = candidate.hostView as? MediaUIView,
              VideoPlaybackArbiter.shared.acquire(self) else { return }

        saveAutoplayPosition()
        autoplaySession.detach()
        if let oldHost = currentAutoplayHostView as? MediaUIView, oldHost !== candidate.hostView {
            oldHost.detachAutoplayPlayer()
        } else if autoplayPlayerView.superview !== candidate.hostView {
            autoplayPlayerView.removeFromSuperview()
        }

        newHost.attachAutoplayPlayer(autoplayPlayerView)
        newHost.setAutoplayOverlay(.loading)

        currentAutoplayID = candidate.id
        currentAutoplayURL = candidate.url
        currentAutoplayHostView = candidate.hostView
        autoplaySession.play(url: candidate.url.absoluteString)
        autoplayPlayerView.canDisplayFrame = autoplaySession.hasRestoredPosition
        autoplayPlayerView.player = autoplaySession.player
        startAutoplayCountdownUpdates()
    }

    private func startAutoplayCountdownUpdates() {
        autoplayCountdownTask?.cancel()
        autoplayCountdownTask = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                self?.updateAutoplayCountdown()
                do {
                    try await Task.sleep(nanoseconds: 250_000_000)
                } catch {
                    return
                }
            }
        }
    }

    private func stopAutoplayCountdownUpdates() {
        autoplayCountdownTask?.cancel()
        autoplayCountdownTask = nil
    }

    private func updateAutoplayCountdown() {
        guard let host = currentAutoplayHostView as? MediaUIView else { return }
        autoplaySession.refresh()
        autoplayPlayerView.canDisplayFrame = autoplaySession.hasRestoredPosition
        switch autoplaySession.state {
        case .playing(let duration): host.setAutoplayOverlay(.playing(remaining: max(duration - autoplaySession.position, 0)))
        case .loading: host.setAutoplayOverlay(.loading)
        case .error: host.setAutoplayOverlay(.error)
        case .idle, .paused: host.setAutoplayOverlay(.idle)
        }
    }

    private func validateCurrentAutoplayVisibility() {
        guard currentAutoplayHostView != nil else { return }
        guard isVideoAutoplayAllowed,
              let host = currentAutoplayHostView,
              (host as? MediaUIView)?.videoURL == currentAutoplayURL,
              host.window != nil,
              visibleRect(for: host, in: collectionView) != nil else {
            stop()
            return
        }
    }

    private func saveAutoplayPosition() {
        autoplaySession.refresh()
        if let currentAutoplayURL, autoplaySession.player != nil {
            MediaPlaybackMemory.shared.save(autoplaySession.position, for: currentAutoplayURL.absoluteString)
        }
    }

    func stop() {
        saveAutoplayPosition()
        _ = autoplayPolicy.select(from: [], isScrolling: true)
        autoplaySelectionTask?.cancel()
        stopAutoplayCountdownUpdates()
        autoplaySession.detach()
        autoplayPlayerView.player = nil
        if let host = currentAutoplayHostView as? MediaUIView {
            host.detachAutoplayPlayer()
        } else {
            autoplayPlayerView.removeFromSuperview()
        }
        currentAutoplayID = nil
        currentAutoplayURL = nil
        currentAutoplayHostView = nil
        VideoPlaybackArbiter.shared.release(self)
    }

    private var autoplayViewport: CGRect {
        // Scroll padding can include artificial space for short profiles. Only
        // the safe area represents bars obscuring the actual viewport.
        collectionView.bounds.inset(by: collectionView.safeAreaInsets)
    }

    private func visibleRect(for hostView: UIView, in collectionView: UICollectionView) -> CGRect? {
        guard !hostView.isHidden,
              hostView.alpha > 0.01,
              hostView.window != nil,
              hostView.bounds.width > 1,
              hostView.bounds.height > 1 else {
            return nil
        }
        var visible = hostView.convert(hostView.bounds, to: collectionView).intersection(autoplayViewport)
        var ancestor = hostView.superview
        while let view = ancestor {
            guard !view.isHidden, view.alpha > 0.01 else { return nil }
            if let media = view as? StatusMediaUIView, !media.allowsVideoAutoplay { return nil }
            if view.clipsToBounds {
                visible = visible.intersection(view.convert(view.bounds, to: collectionView))
            }
            if view === collectionView { break }
            ancestor = view.superview
        }
        guard !visible.isEmpty else { return nil }
        return visible
    }

}
