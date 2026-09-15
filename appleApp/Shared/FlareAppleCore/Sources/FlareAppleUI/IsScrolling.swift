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

@available(iOS 17.0, macOS 14.0, *)
private struct DetectScrollingModifier: ViewModifier {
    let debounceIdleSeconds: TimeInterval

    @State private var rawIsScrolling = false
    @State private var isScrolling = false
    @State private var isScrollingState = IsScrollingState()
    @State private var debounceTask: Task<Void, Never>?

    func body(content: Content) -> some View {
        if #available(iOS 18.0, macOS 15.0, *) {
            content
                .environment(\.isScrolling, isScrolling)
                .environment(\.isScrollingState, isScrollingState)
                .onScrollPhaseChange { _, phase in
                    rawIsScrolling = (phase != .idle)
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
    func detectScrolling(debounceIdle: TimeInterval = 0.500) -> some View {
        if #available(iOS 17.0, macOS 14.0, *) {
            modifier(DetectScrollingModifier(debounceIdleSeconds: debounceIdle))
        } else {
            environment(\.isScrolling, false)
        }
    }
}
