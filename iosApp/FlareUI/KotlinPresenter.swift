@preconcurrency import KotlinSharedUI
import Foundation
@preconcurrency import Combine

// using @Observable might init presenter multiple times
// which is a heavy work since Kotlin Presenter is not designed to do so
// so we keep using the old ObservableObject and @StateObject
// see: https://github.com/Dimillian/IceCubesApp/issues/2033
public final class KotlinPresenter<T: AnyObject>: ObservableObject {
    private var subscribers = Set<AnyCancellable>()
    var presenter: PresenterBase<T>
    public let key = UUID().uuidString

    public init(presenter: PresenterBase<T>) {
        self.presenter = presenter
        self.state = presenter.models.value
        self.presenter.models.toPublisher().receive(on: DispatchQueue.main).sink { [weak self] newState in
            guard let self, self.state !== newState else { return }
            self.state = newState
        }.store(in: &subscribers)
    }

    @Published
    public var state: T

//    @MainActor
    deinit {
        subscribers.forEach { cancellable in
            cancellable.cancel()
        }
        presenter.close()
    }
}
