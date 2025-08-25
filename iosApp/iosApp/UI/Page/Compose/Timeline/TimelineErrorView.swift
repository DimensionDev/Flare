import SwiftUI

struct TimelineErrorView: View {
    let message: String
    let onRetry: () -> Void


    init(message: String = "Failed to load", onRetry: @escaping () -> Void) {
        self.message = message
        self.onRetry = onRetry
    }


    init(error: FlareError, onRetry: @escaping () -> Void) {
        message = error.localizedDescription
        self.onRetry = onRetry
    }

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.largeTitle)
                .foregroundColor(.orange)

            Text("Failed to load")
                .font(.headline)

            Text(message)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Button("Retry", action: onRetry)
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, minHeight: 200)
        .padding()
    }
}
