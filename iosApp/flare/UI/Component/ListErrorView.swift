import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct ListErrorView: View {
    let error: KotlinThrowable
    @Environment(\.openURL) private var openURL
    let onRetry: () -> Void
    var body: some View {
        VStack(spacing: 8) {
            if let expiredError = error as? LoginExpiredException {
                Image(systemName: "person.badge.shield.exclamationmark")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 64, height: 64)
                Text("error_login_expired \(expiredError.accountKey)")
                    .multilineTextAlignment(.center)
                    .font(.headline)
                Button {
                    openURL(URL(string: AppDeepLink.shared.LOGIN)!)
                } label: {
                    Text("error_login_expired_action")
                }
                .backport
                .glassProminentButtonStyle()
            } else {
                Image(systemName: "exclamationmark.triangle.text.page")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 64, height: 64)
                Text("error_generic")
                    .multilineTextAlignment(.center)
                    .font(.headline)
                Button {
                    onRetry()
                } label: {
                    Text("action_retry")
                }
                .backport
                .glassProminentButtonStyle()
                if let message = error.message {
                    Text(message)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding()
    }
}
