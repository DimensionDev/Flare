import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct UserCompatView<TrailingContent: View>: View {
    private let data: UiProfile
    private let trailing: () -> TrailingContent
    private let onClicked: (() -> Void)?

    public init(
        data: UiProfile,
        @ViewBuilder trailing: @escaping () -> TrailingContent,
        onClicked: (() -> Void)? = nil
    ) {
        self.data = data
        self.trailing = trailing
        self.onClicked = onClicked
    }

    public var body: some View {
        HStack(spacing: 8) {
            if let onClicked {
                Button(action: onClicked) {
                    identityContent
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: profileActionLabel))
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                identityContent
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            trailing()
        }
        .lineLimit(1)
    }

    private var identityContent: some View {
        HStack(spacing: 8) {
            AvatarView(data: data.avatar?.url, customHeader: data.avatar?.customHeaders)
                .frame(width: 44, height: 44)
            VStack(
                alignment: .leading,
                spacing: 0
            ) {
                RichText(text: data.name)
                Text(data.handle.canonical)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var profileActionLabel: String {
        String(
            format: String(
                localized: "profile_open_user",
                defaultValue: "Open profile for %@"
            ),
            locale: .current,
            data.handle.canonical
        )
    }
}

public extension UserCompatView {
    init(data: UiProfile) where TrailingContent == EmptyView {
        self.init(data: data) {
            EmptyView()
        }
    }

    init(
        data: UiProfile,
        onClicked: @escaping () -> Void
    ) where TrailingContent == EmptyView {
        self.init(
            data: data,
            trailing: { EmptyView() },
            onClicked: onClicked
        )
    }
}

public struct UserLoadingView: View {
    public init() {}

    public var body: some View {
        HStack(spacing: 8) {
            Rectangle()
                .fill(.placeholder)
                .frame(width: 44, height: 44)
                .clipShape(.circle)
            VStack(
                alignment: .leading,
                spacing: 0
            ) {
                Text("#loading", bundle: FlareAppleUILocalization.bundle)
                Text("#loading", bundle: FlareAppleUILocalization.bundle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .redacted(reason: .placeholder)
    }
}

public struct UserErrorView: View {
    private let error: KotlinThrowable

    public init(error: KotlinThrowable) {
        self.error = error
    }

    public var body: some View {
        if let expiredError = error as? LoginExpiredException {
            HStack(spacing: 8) {
                Image(systemName: "person.badge.shield.exclamationmark")
                    .scaledToFit()
                    .frame(width: 44, height: 44)
                VStack(
                    alignment: .leading,
                    spacing: 0
                ) {
                    Text("notification_login_expired", bundle: FlareAppleUILocalization.bundle)
                    Text("error_login_expired \(expiredError.accountKey)", bundle: FlareAppleUILocalization.bundle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        } else {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle")
                    .scaledToFit()
                    .frame(width: 44, height: 44)
                VStack(
                    alignment: .leading,
                    spacing: 0
                ) {
                    Text("error", bundle: FlareAppleUILocalization.bundle)
                    Text(error.message ?? "Unknown error")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}
