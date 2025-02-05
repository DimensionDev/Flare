import shared
import SwiftUI

struct ObservePresenter<Value, Presenter: PresenterBase<Value>, Content: View>: View {
    let presenter: Presenter
    @State private var state: Value
    private let content: (Value) -> Content

    init(presenter: Presenter, @ViewBuilder content: @escaping (Value) -> Content) {
        self.presenter = presenter
        state = self.presenter.models.value
        self.content = content
    }

    public var body: some View {
        content(state)
            .collect(flow: presenter.models, into: $state)
    }
}
