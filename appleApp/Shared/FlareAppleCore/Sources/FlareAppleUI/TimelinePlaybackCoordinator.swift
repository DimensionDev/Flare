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
    private var positions: [String: Double] = [:]
    private var suspended = false

    init(arbiter: VideoPlaybackArbiter = .shared) {
        self.arbiter = arbiter
        arbiter.register(self, stop: { [weak self] in self?.stopCurrentPlayer() },
                         reconsider: { [weak self] in self?.scheduleSelection() })
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
            let motionSource = "motion:\(source)"
            motionTasks.removeValue(forKey: motionSource)?.cancel()
            scrollingSources.remove(motionSource)
            if scrollingSources.insert(source).inserted {
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
        let motionSource = "motion:\(source)"
        if !scrollingSources.contains(source), !scrollingSources.contains(motionSource) {
            arbiter.interacted(self)
            if vertical { policy.verticalScrollBegan() }
            else { policy.interactedWithCarousel(source) }
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

    func position(for key: String) -> Double { positions[key] ?? 0 }

    func savePosition(_ seconds: Double, for key: String) {
        if seconds.isFinite, seconds >= 0 { positions[key] = seconds }
    }

    private func scheduleSelection() {
        selectionTask?.cancel()
        guard scrollingSources.isEmpty, !suspended else { return }
        selectionTask = Task { [weak self] in
            do { try await Task.sleep(for: .milliseconds(200)) } catch { return }
            self?.reconcile(allowStart: true)
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
        if let next { players[next]?(true) }
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
