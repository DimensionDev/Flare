import KotlinSharedUI
import Foundation
import Combine

@Observable
final class KotlinPresenter<T: AnyObject> {
    var subscribers = Set<AnyCancellable>()
    var presenter: PresenterBase<T>
    let key = UUID().uuidString

    init(presenter: PresenterBase<T>) {
        self.presenter = presenter
        self.state = presenter.models.value
        self.presenter.models.toPublisher().receive(on: DispatchQueue.main).sink { [weak self] newState in
            self?.state = newState
        }.store(in: &subscribers)
    }

    var state: T

    @MainActor
    deinit {
        subscribers.forEach { cancleable in
            cancleable.cancel()
        }
        presenter.close()
    }
}
