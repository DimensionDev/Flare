import FlareRust
import Foundation
import Observation

protocol RustStateSubscription: AnyObject, Sendable {
    associatedtype State: Sendable
    nonisolated func nextState() async -> State?
    nonisolated func stop()
}

protocol RustPresenterSource: AnyObject, Sendable {
    associatedtype Subscription: RustStateSubscription
    nonisolated func snapshot() -> Subscription.State
    nonisolated func subscribe() -> Subscription
    nonisolated func shutdown()
}

/// The retained SwiftUI owner manages the session; a view task only manages observation.
@MainActor
@Observable
final class RustPresenter<P: RustPresenterSource> {
    private(set) var state: P.Subscription.State?
    private(set) var error: UiError?
    @ObservationIgnored private var makePresenter: (() throws -> P)?
    @ObservationIgnored private var presenter: P?
    @ObservationIgnored private var actions: [UUID: Task<Void, Never>] = [:]

    // SwiftUI may evaluate @State initializers repeatedly; create Rust resources only when used.
    init(factory: @escaping () throws -> P) {
        makePresenter = factory
    }

    func observe() async {
        guard !Task.isCancelled else { return }
        do {
            if presenter == nil, let makePresenter {
                presenter = try makePresenter()
                self.makePresenter = nil
            }
            guard let presenter else { return }
            let subscription = presenter.subscribe()
            state = presenter.snapshot()
            await withTaskCancellationHandler {
                defer { subscription.stop() }
                while !Task.isCancelled, let next = await subscription.nextState() {
                    if Task.isCancelled { break }
                    state = next
                }
            } onCancel: {
                subscription.stop()
            }
        } catch {
            if !Task.isCancelled { self.error = uiError(error) }
        }
    }

    func perform(_ action: @escaping @Sendable (P) async throws -> Void) {
        guard let presenter else { return }
        error = nil
        let id = UUID()
        actions[id] = Task { [weak self] in
            defer { self?.actions[id] = nil }
            do {
                try await action(presenter)
            } catch {
                if !Task.isCancelled { self?.error = uiError(error) }
            }
        }
    }

    deinit {
        for task in actions.values { task.cancel() }
        presenter?.shutdown()
    }
}

private func uiError(_ error: any Error) -> UiError {
    switch error {
    case PresenterError.Failed(let error): error
    case PresenterError.Closed:
        UiError(code: "presenter_closed", message: "Presenter is closed")
    case PresenterError.RuntimeUnavailable:
        UiError(code: "runtime_unavailable", message: "Rust runtime could not start")
    default:
        UiError(code: "native_error", message: error.localizedDescription)
    }
}
