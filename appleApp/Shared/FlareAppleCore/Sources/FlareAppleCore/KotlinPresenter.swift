@preconcurrency import Combine
import Foundation
@preconcurrency import KotlinSharedUI

// Keep PresenterBase owned by a stable ObservableObject so SwiftUI view reloads
// do not recreate heavy Kotlin presenters.
public final class KotlinPresenter<T: AnyObject>: ObservableObject {
    private var subscribers = Set<AnyCancellable>()

    public let presenter: PresenterBase<T>
    public let key = UUID().uuidString

    public init(presenter: PresenterBase<T>) {
        self.presenter = presenter
        self.state = presenter.models.value
        self.presenter.models.toPublisher()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] newState in
                guard let self, self.state !== newState else { return }
                self.state = newState
            }
            .store(in: &subscribers)
    }

    @Published public var state: T

    deinit {
        subscribers.forEach { cancellable in
            cancellable.cancel()
        }
        presenter.close()
    }
}
