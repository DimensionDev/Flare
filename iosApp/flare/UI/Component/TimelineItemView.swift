import SwiftUI
import KotlinSharedUI
import Combine

struct TimelineView : UIViewControllerRepresentable {
    let key: String
    let data: TimelineItemPresenterState
    let state: ComposeUIStateProxy<TimelineItemPresenterState>
    
    init(key: String, data: TimelineItemPresenterState, onOpenLink: @escaping (String) -> Void) {
        self.key = key
        self.data = data
        self.state = ComposeUIStateProxyCache.shared.getOrCreate(key: key, factory: {
            .init(initialState: data, onOpenLink: onOpenLink)
        }) as! ComposeUIStateProxy<any TimelineItemPresenterState>
    }
    
    func makeUIViewController(context: Context) -> some UIViewController {
        return KotlinSharedUI.TimelineController(state: state)
    }
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
        state.update(newState: data)
    }
    
    func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) {
        ComposeUIStateProxyCache.shared.remove(key: key)
    }
}

final class KotlinPresenter<T: AnyObject>: ObservableObject {
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
    
    @Published
    var state: T
    
    @MainActor
    deinit {
        subscribers.forEach { cancleable in
            cancleable.cancel()
        }
        presenter.close()
    }
}
