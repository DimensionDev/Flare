import FlareCounterTypes
import SwiftUI

struct CounterScreen: View {
    @State private var viewModel: CounterViewModel?
    private let initialValue: Int64

    init(initialValue: Int64 = 0) {
        self.initialValue = initialValue
    }

    var body: some View {
        VStack(spacing: 24) {
            if let viewModel {
                let state = viewModel.view
                Text(state.value.formatted())
                    .font(.system(size: 64, weight: .medium, design: .rounded))
                    .monospacedDigit()
                    .accessibilityIdentifier("counter-value")
                HStack(spacing: 24) {
                    Button("Decrease", systemImage: "minus") {
                        viewModel.update(.decrement)
                    }
                    .accessibilityIdentifier("counter-decrement")
                    Button("Increase", systemImage: "plus") {
                        viewModel.update(.increment)
                    }
                    .accessibilityIdentifier("counter-increment")
                }
                .buttonStyle(.borderedProminent)
                Button("Reset") { viewModel.update(.reset) }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("counter-reset")
                if let error = state.error {
                    Text(error.message).foregroundStyle(.red)
                        .accessibilityIdentifier("counter-error")
                }
            } else {
                ProgressView()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
        .navigationTitle("Counter")
        .onAppear {
            guard viewModel == nil else { return }
            viewModel = CounterViewModel(.initialize(initialValue))
        }
    }
}
