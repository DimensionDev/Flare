import FlareRust
import SwiftUI

struct CounterScreen: View {
    @State private var model: RustPresenter<CounterPresenter>

    init(initialValue: Int64 = 0) {
        _model = State(initialValue: RustPresenter { try CounterPresenter(initialValue: initialValue) })
    }

    var body: some View {
        VStack(spacing: 24) {
            switch model.state {
            case .success(let state):
                Text(state.value.formatted())
                    .font(.system(size: 64, weight: .medium, design: .rounded))
                    .monospacedDigit()
                    .accessibilityIdentifier("counter-value")
                HStack(spacing: 24) {
                    Button("Decrease", systemImage: "minus") {
                        model.perform { try await $0.decrement() }
                    }
                    .accessibilityIdentifier("counter-decrement")
                    Button("Increase", systemImage: "plus") {
                        model.perform { try await $0.increment() }
                    }
                    .accessibilityIdentifier("counter-increment")
                }
                .buttonStyle(.borderedProminent)
                Button("Reset") { model.perform { try await $0.reset() } }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("counter-reset")
            case .error(let error):
                Text(error.message).foregroundStyle(.red)
            case .loading, nil:
                if model.error == nil { ProgressView() }
            }
            if let error = model.error {
                Text(error.message).foregroundStyle(.red)
                    .accessibilityIdentifier("counter-error")
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
        .navigationTitle("Counter")
        .task { await model.observe() }
    }
}
