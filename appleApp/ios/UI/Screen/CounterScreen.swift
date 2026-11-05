import FlareCounterTypes
import SwiftUI

struct CounterScreen: View {
    @StateObject private var model: CruxModel<CounterState, CounterEvent>

    init(initialValue: Int64 = 0) {
        _model = StateObject(wrappedValue: CruxModel(binding: counterBinding(initialValue: initialValue)))
    }

    var body: some View {
        VStack(spacing: 24) {
            if let state = model.state {
                Text(state.value.formatted())
                    .font(.system(size: 64, weight: .medium, design: .rounded))
                    .monospacedDigit()
                    .accessibilityIdentifier("counter-value")
                HStack(spacing: 24) {
                    Button("Decrease", systemImage: "minus") {
                        model.send(.decrement)
                    }
                    .accessibilityIdentifier("counter-decrement")
                    Button("Increase", systemImage: "plus") {
                        model.send(.increment)
                    }
                    .accessibilityIdentifier("counter-increment")
                }
                .buttonStyle(.borderedProminent)
                Button("Reset") { model.send(.reset) }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("counter-reset")
            } else if model.error == nil {
                ProgressView()
            }
            if let error = model.error ?? model.state?.error?.message {
                Text(error).foregroundStyle(.red)
                    .accessibilityIdentifier("counter-error")
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
        .navigationTitle("Counter")
    }
}
