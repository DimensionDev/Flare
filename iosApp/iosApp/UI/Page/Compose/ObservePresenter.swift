import shared
import SwiftUI

struct ObservePresenter<Value, Presenter: PresenterBase<Value>, Content: View>: View {
    let presenter: Presenter
    @State private var state: Result<Value, Error>? = nil
    private let content: (Value) -> Content

    init(presenter: Presenter, @ViewBuilder content: @escaping (Value) -> Content) {
        self.presenter = presenter
        self.content = content
    }

    public var body: some View {
        Group {
            if let state {
                switch state {
                case let .success(value):
                    content(value)
                case let .failure(error):
                    VStack {
                        Text("An error occurred")
                        Text(error.localizedDescription)
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }
            } else {
                ProgressView()
            }
        }
        .task {
            do {
                for try await model in presenter.models {
                    state = .success(model)
                }
            } catch {
                FlareLog.error("ObservePresenter error: \(error)")
                state = .failure(error)
            }
        }
    }
}
