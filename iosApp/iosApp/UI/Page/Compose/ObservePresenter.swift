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
            if let state = state {
                switch state {
                case .success(let value):
                    content(value)
                case .failure(let error):
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
                    self.state = .success(model)
                }
            } catch {
                FlareLog.error("ObservePresenter error: \(error)")
                self.state = .failure(error)
            }
        }
    }
}