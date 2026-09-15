@preconcurrency import Combine
import Foundation
import Observation
@preconcurrency import KotlinSharedUI

@Observable
public final class KotlinPresenter<T: AnyObject> {
    @ObservationIgnored private var makePresenter: (() -> PresenterBase<T>)?
    @ObservationIgnored private var storage: Storage?
    @ObservationIgnored private var subscription: AnyCancellable?

    public let key = UUID().uuidString

    // Parameterized @State initializers can run on every view rebuild. Defer the
    // Kotlin factory until the retained wrapper is actually used.
    public init(presenter: @autoclosure @escaping () -> PresenterBase<T>) {
        self.makePresenter = presenter
    }

    public var presenter: PresenterBase<T> {
        initializedStorage().presenter
    }

    public var state: T {
        get {
            access(keyPath: \.state)
            return initializedStorage().state
        }
        set {
            let storage = initializedStorage()
            withMutation(keyPath: \.state) {
                storage.state = newValue
            }
        }
    }

    // Keep Combine's initial-value replay and will-set delivery for UIKit consumers.
    public var statePublisher: AnyPublisher<T, Never> {
        initializedStorage().$state.eraseToAnyPublisher()
    }

    private func initializedStorage() -> Storage {
        if let storage { return storage }
        guard let makePresenter else {
            preconditionFailure("Missing Kotlin presenter factory")
        }
        let storage = Storage(presenter: makePresenter())
        self.storage = storage
        self.makePresenter = nil
        subscription = storage.presenter.models.toPublisher()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] newState in
                guard let self, self.state !== newState else { return }
                self.state = newState
            }
        return storage
    }

    deinit {
        subscription?.cancel()
    }

    private final class Storage {
        let presenter: PresenterBase<T>
        @Published var state: T

        init(presenter: PresenterBase<T>) {
            self.presenter = presenter
            self.state = presenter.models.value
        }

        deinit {
            presenter.close()
        }
    }
}
