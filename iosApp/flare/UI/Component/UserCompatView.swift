import SwiftUI
import KotlinSharedUI

struct UserCompatView<TrailingContent: View>: View {
    @Environment(\.openURL) private var openURL
    let data: UiUserV2
    let trailing: () -> TrailingContent
    let onClicked: (() -> Void)?
    var body: some View {
        HStack {
            AvatarView(data: data.avatar)
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
                Text(data.handle)
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

extension UserCompatView {
    init(data: UiUserV2) where TrailingContent == EmptyView {
        self.data = data
        self.trailing = {
            EmptyView()
        }
        self.onClicked = nil
    }
}

struct UserLoadingView: View {
    var body: some View {
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

struct UserErrorView: View {
    let error: KotlinThrowable
    var body: some View {
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
