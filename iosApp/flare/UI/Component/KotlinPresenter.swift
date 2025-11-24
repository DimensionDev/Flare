@preconcurrency import KotlinSharedUI
import Foundation
@preconcurrency import Combine

// using @Observable might init presenter multiple times
// which is a heavy work since Kotlin Presenter is not designed to do so
// so we keep using the old ObservableObject and @StateObject
// see: https://github.com/Dimillian/IceCubesApp/issues/2033
final class KotlinPresenter<T: AnyObject>: ObservableObject {
    private var subscribers = Set<AnyCancellable>()
    var presenter: PresenterBase<T>
    let key = UUID().uuidString

    init(presenter: PresenterBase<T>) {
        self.presenter = presenter
        self.state = presenter.models.value
        self.presenter.models.toPublisher().receive(on: DispatchQueue.main).sink { [weak self] newState in
            self?.state = newState
        }.store(in: &subscribers)
    }

    @Published
    var state: T

//    @MainActor
    deinit {
        subscribers.forEach { cancleable in
            cancleable.cancel()
        }
        presenter.close()
    }
}
