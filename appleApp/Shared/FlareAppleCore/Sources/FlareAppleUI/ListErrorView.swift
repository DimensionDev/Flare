import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

public struct ListErrorView: View {
    private let error: KotlinThrowable
    @Environment(\.openURL) private var openURL
    private let onRetry: () -> Void

    public init(error: KotlinThrowable, onRetry: @escaping () -> Void) {
        self.error = error
        self.onRetry = onRetry
    }

    public var body: some View {
        VStack(spacing: 8) {
            if let expiredError = error as? LoginExpiredException {
                Image(systemName: "person.badge.shield.exclamationmark")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 64, height: 64)
                Text("error_login_expired \(expiredError.accountKey)", bundle: FlareAppleUILocalization.bundle)
                    .multilineTextAlignment(.center)
                    .font(.headline)
                Button {
                    if let url = reloginURL(
                        accountKey: expiredError.accountKey,
                        platformType: expiredError.platformType
                    ) {
                        openURL(url)
                    }
                } label: {
                    Text("error_login_expired_action", bundle: FlareAppleUILocalization.bundle)
                }
                .backport
                .glassProminentButtonStyle()
            } else if let reloginError = error as? RequireReLoginException {
                Image(systemName: "person.badge.shield.exclamationmark")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 64, height: 64)
                Text("permission_denied_title", bundle: FlareAppleUILocalization.bundle)
                    .multilineTextAlignment(.center)
                    .font(.headline)
                Text("permission_denied_message", bundle: FlareAppleUILocalization.bundle)
                Button {
                    if let url = reloginURL(
                        accountKey: reloginError.accountKey,
                        platformType: reloginError.platformType
                    ) {
                        openURL(url)
                    }
                } label: {
                    Text("error_login_expired_action", bundle: FlareAppleUILocalization.bundle)
                }
                .backport
                .glassProminentButtonStyle()
            } else  {
                Image(systemName: "exclamationmark.triangle.text.page")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 64, height: 64)
                Text("error", bundle: FlareAppleUILocalization.bundle)
                    .multilineTextAlignment(.center)
                    .font(.headline)
                Button {
                    onRetry()
                } label: {
                    Text("action_retry", bundle: FlareAppleUILocalization.bundle)
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

    private func reloginURL(accountKey: MicroBlogKey, platformType: PlatformType) -> URL? {
        URL(
            string: DeeplinkRoute.Relogin(
                accountKey: accountKey,
                platformType: platformType
            ).toUri()
        )
    }
}
