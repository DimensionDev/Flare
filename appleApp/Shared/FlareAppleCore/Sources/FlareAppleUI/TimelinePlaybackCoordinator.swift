import SwiftUI

struct TimelineScrollPhaseModifier: ViewModifier {
    let onChange: (Bool) -> Void

    func body(content: Content) -> some View {
        if #available(iOS 18.0, macOS 15.0, *) {
            content.onScrollPhaseChange { _, phase in onChange(phase != .idle) }
        } else {
            content
        }
    }
}

@MainActor
final class TimelinePlaybackCoordinator {
    private let arbiter: VideoPlaybackArbiter
    private var policy = TimelineAutoplayPolicy()
    private var candidates: [String: TimelineAutoplayPolicy.Candidate] = [:]
    private var players: [String: (Bool) -> Void] = [:]
    private var scrollingSources: Set<String> = []
    private var motionTasks: [String: Task<Void, Never>] = [:]
    private var selectionTask: Task<Void, Never>?
    private var playingID: String?
    private var returningGroupID: String?
    let mediaSelections = TimelineMediaSelections()
    private var suspended = false
    private var immediateReturn = false

    init(arbiter: VideoPlaybackArbiter = .shared) {
        self.arbiter = arbiter
        arbiter.register(self, stop: { [weak self] in self?.stopCurrentPlayer() },
                         reconsider: { [weak self] in self?.scheduleSelection() },
                         mediaReturned: { [weak self] urls, selected in
                             self?.mediaSelections.returned(urls: urls, selectedURL: selected)
                         }, resume: { [weak self] in
                             self?.immediateReturn = true
                             self?.scheduleSelection()
                         }, willHandoff: { VideoPlaybackSession.continuePlayback(to: $0) })
    }

    func register(id: String, playback: @escaping (Bool) -> Void) {
        players[id] = playback
    }

    func update(_ candidate: TimelineAutoplayPolicy.Candidate) {
        guard candidates[candidate.id] != candidate else { return }
        candidates[candidate.id] = candidate
        reconcile(allowStart: false)
        scheduleSelection()
    }

    func remove(id: String) {
        candidates.removeValue(forKey: id)
        reconcile(allowStart: false)
        players.removeValue(forKey: id)
        scheduleSelection()
    }

    func setScrolling(_ scrolling: Bool, source: String, vertical: Bool) {
        if scrolling {
            immediateReturn = false
            let motionSource = "motion:\(source)"
            motionTasks.removeValue(forKey: motionSource)?.cancel()
            scrollingSources.remove(motionSource)
            if scrollingSources.insert(source).inserted {
                returningGroupID = nil
                arbiter.interacted(self)
                if vertical { policy.verticalScrollBegan() }
                else { policy.interactedWithCarousel(source) }
            }
            selectionTask?.cancel()
        } else {
            scrollingSources.remove(source)
            scheduleSelection()
        }
    }

    // Geometry also covers macOS 14 / iOS 17, which have no scroll-phase API,
    // and programmatic scrolling. Real drag phases keep the source held longer.
    func moved(source: String, vertical: Bool) {
        guard !scrollingSources.contains(source) else { return }
        if !vertical, returningGroupID == source, immediateReturn {
            // Restoring the viewer's selected page is an immediate jump. Actual
            // drag/animation phases above still hold playback until scrolling ends.
            scheduleSelection()
            return
        }
        let motionSource = "motion:\(source)"
        if !scrollingSources.contains(source), !scrollingSources.contains(motionSource) {
            if vertical || returningGroupID != source {
                arbiter.interacted(self)
                if vertical { policy.verticalScrollBegan() }
                else { policy.interactedWithCarousel(source) }
            }
        }
        scrollingSources.insert(motionSource)
        selectionTask?.cancel()
        motionTasks[motionSource]?.cancel()
        motionTasks[motionSource] = Task { [weak self] in
            do { try await Task.sleep(for: .milliseconds(200)) } catch { return }
            guard let self else { return }
            self.scrollingSources.remove(motionSource)
            self.motionTasks.removeValue(forKey: motionSource)
            self.reconcile(allowStart: self.scrollingSources.isEmpty)
            if self.returningGroupID == source { self.returningGroupID = nil }
        }
    }

    func setSuspended(_ value: Bool) {
        suspended = value
        if value {
            selectionTask?.cancel()
            motionTasks.values.forEach { $0.cancel() }
            motionTasks.removeAll()
            scrollingSources.removeAll()
            switchPlayer(to: nil)
            arbiter.withdraw(self)
            _ = policy.select(from: [], isScrolling: true)
        } else {
            scheduleSelection()
        }
    }

    func selectMedia(groupID: String, mediaURL: String, userInitiated: Bool) {
        returningGroupID = userInitiated ? nil : groupID
        if userInitiated {
            immediateReturn = false
            arbiter.interacted(self)
        }
        policy.returnedToMedia(groupID: groupID, mediaURL: mediaURL)
        scheduleSelection()
    }

    private func scheduleSelection() {
        selectionTask?.cancel()
        guard scrollingSources.isEmpty, !suspended else { return }
        if immediateReturn {
            reconcile(allowStart: true)
            if playingID != nil { immediateReturn = false }
            return
        }
        selectionTask = Task { [weak self] in
            do { try await Task.sleep(for: .milliseconds(200)) } catch { return }
            self?.reconcile(allowStart: true)
            self?.returningGroupID = nil
        }
    }

    private func reconcile(allowStart: Bool) {
        guard !suspended else { return }
        // Geometry updates only need to validate the current video. Select from
        // the complete candidate set once scrolling has settled.
        guard allowStart, scrollingSources.isEmpty else {
            if let playingID, candidates[playingID]?.isVisible != true { switchPlayer(to: nil) }
            return
        }
        let next = policy.select(from: Array(candidates.values),
                                 isScrolling: false)
        if next == nil { arbiter.settledWithoutVideo(self) }
        switchPlayer(to: next)
    }

    private func switchPlayer(to next: String?) {
        guard next != playingID else { return }
        if next != nil, !arbiter.acquire(self) {
            stopCurrentPlayer()
            return
        }
        stopCurrentPlayer(resetPolicy: next == nil)
        playingID = next
        if let next {
            immediateReturn = false
            players[next]?(true)
        }
        else { arbiter.release(self) }
    }

    private func stopCurrentPlayer(resetPolicy: Bool = true) {
        let old = playingID
        playingID = nil
        if let old { players[old]?(false) }
        if resetPolicy { _ = policy.select(from: [], isScrolling: true) }
    }
}

private struct TimelinePlaybackCoordinatorKey: EnvironmentKey {
    static let defaultValue: TimelinePlaybackCoordinator? = nil
}

struct TimelineCarouselItem: Equatable {
    let groupID: String
    let isSelected: Bool
    var isCarousel = true
}

private struct TimelineCarouselItemKey: EnvironmentKey {
    static let defaultValue: TimelineCarouselItem? = nil
}

extension EnvironmentValues {
    var timelinePlaybackCoordinator: TimelinePlaybackCoordinator? {
        get { self[TimelinePlaybackCoordinatorKey.self] }
        set { self[TimelinePlaybackCoordinatorKey.self] = newValue }
    }

    var timelineCarouselItem: TimelineCarouselItem? {
        get { self[TimelineCarouselItemKey.self] }
        set { self[TimelineCarouselItemKey.self] = newValue }
    }
}
