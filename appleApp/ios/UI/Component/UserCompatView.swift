import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI

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
        HStack {
            AvatarView(data: data.avatar?.url, customHeader: data.avatar?.customHeaders)
                .frame(width: 44, height: 44)
                .if(onClicked != nil) { view in
                    view
                        .onTapGesture {
                            onClicked?()
                        }
                }
            VStack(
                alignment: .leading
            ) {
                RichText(text: data.name)
                Text(data.handle.canonical)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .if(onClicked != nil) { view in
                view
                    .onTapGesture {
                        onClicked?()
                    }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .lineLimit(1)
    }
}

public extension UserCompatView {
    init(data: UiProfile) where TrailingContent == EmptyView {
        self.init(data: data) {
            EmptyView()
        }
    }
}

public struct UserLoadingView: View {
    public init() {}

    public var body: some View {
        HStack {
            Rectangle()
                .fill(.placeholder)
                .frame(width: 44, height: 44)
                .clipShape(.circle)
            VStack(
                alignment: .leading
            ) {
                Text("#loading")
                Text("#loading")
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
            HStack {
                Image(systemName: "person.badge.shield.exclamationmark")
                    .scaledToFit()
                    .frame(width: 44, height: 44)
                VStack(
                    alignment: .leading
                ) {
                    Text("notification_login_expired")
                    Text("error_login_expired \(expiredError.accountKey)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        } else {
            HStack {
                Image(systemName: "exclamationmark.triangle")
                    .scaledToFit()
                    .frame(width: 44, height: 44)
                VStack(
                    alignment: .leading
                ) {
                    Text("error_generic")
                    Text(error.message ?? "Unknown error")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}
