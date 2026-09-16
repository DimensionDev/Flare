import SwiftUI

@Observable
public final class IsScrollingState {
    public var isScrolling: Bool

    public init(isScrolling: Bool = false) {
        self.isScrolling = isScrolling
    }
}

public extension EnvironmentValues {
    @Entry var isScrolling = false
    @Entry var isScrollingState: IsScrollingState? = nil
}

extension EnvironmentValues {
    @Entry var timelinePlaybackViewport: CGRect? = nil
}

@available(iOS 17.0, macOS 14.0, *)
private struct DetectScrollingModifier: ViewModifier {
    let debounceIdleSeconds: TimeInterval

    @Environment(\.scenePhase) private var scenePhase
    @State private var playback = TimelinePlaybackCoordinator()
    @State private var rawIsScrolling = false
    @State private var isScrolling = false
    @State private var isScrollingState = IsScrollingState()
    @State private var debounceTask: Task<Void, Never>?
    @State private var playbackViewport: CGRect?

    func body(content: Content) -> some View {
        scrollingContent(content)
            .environment(\.timelinePlaybackCoordinator, playback)
            .environment(\.timelinePlaybackViewport, playbackViewport)
            .onGeometryChange(for: CGRect.self) { proxy in
                // SwiftUI's container frame already excludes its safe-area insets.
                proxy.frame(in: .global)
            } action: { playbackViewport = $0 }
            .onAppear { playback.setSuspended(scenePhase != .active) }
            .onChange(of: scenePhase) { _, phase in playback.setSuspended(phase != .active) }
            .onDisappear { playback.setSuspended(true) }
    }

    @ViewBuilder
    private func scrollingContent(_ content: Content) -> some View {
        if #available(iOS 18.0, macOS 15.0, *) {
            content
                .environment(\.isScrolling, isScrolling)
                .environment(\.isScrollingState, isScrollingState)
                .onScrollPhaseChange { _, phase in
                    rawIsScrolling = (phase != .idle)
                    playback.setScrolling(phase != .idle, source: "vertical", vertical: true)
                }
                .onScrollGeometryChange(for: CGFloat.self) { $0.contentOffset.y } action: { old, new in
                    if old != new { playback.moved(source: "vertical", vertical: true) }
                }
                .onChange(of: rawIsScrolling) { _, newValue in
                    if newValue {
                        debounceTask?.cancel()
                        isScrolling = true
                        isScrollingState.isScrolling = true
                    } else {
                        debounceTask?.cancel()
                        debounceTask = Task {
                            let ns = UInt64((debounceIdleSeconds * 1_000).rounded()) * 1_000_000
                            try? await Task.sleep(nanoseconds: ns)
                            if !Task.isCancelled && !rawIsScrolling {
                                isScrolling = false
                                isScrollingState.isScrolling = false
                            }
                        }
                    }
                }
                .onDisappear {
                    debounceTask?.cancel()
                    rawIsScrolling = false
                    isScrolling = false
                    isScrollingState.isScrolling = false
                }
        } else {
            content
                .environment(\.isScrolling, false)
        }
    }
}

public extension View {
    @ViewBuilder
    func detectScrolling(debounceIdle: TimeInterval = 0.200) -> some View {
        if #available(iOS 17.0, macOS 14.0, *) {
            modifier(DetectScrollingModifier(debounceIdleSeconds: debounceIdle))
        } else {
            environment(\.isScrolling, false)
        }
    }
}
