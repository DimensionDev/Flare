import shared
import SwiftUI

struct UserListView: View {
    let presenter: UserListPresenter
    @Environment(FlareTheme.self) private var theme
    @Environment(FlareRouter.self) private var router

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                switch onEnum(of: state.listState) {
                case .loading:
                    ForEach(0 ..< 10, id: \.self) { _ in
                        UserRowView(user: createSampleUser())
                            .listRowBackground(theme.primaryBackgroundColor)
                            .redacted(reason: .placeholder)
                    }

                case let .success(successData):
                    ForEach(0 ..< successData.itemCount, id: \.self) { index in
                        if let user = successData.peek(index: Int32(index)) {
                            UserRowView(user: user)
                                .listRowBackground(theme.primaryBackgroundColor)
                        }
                    }

                case let .error(error):
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundColor(.orange)
                        Text("Failed to load users")
                            .font(.headline)
                        Button("Retry") {
                            error.onRetry()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .listRowBackground(Color.clear)

                case .empty:
                    VStack(spacing: 16) {
                        Image(systemName: "person.slash")
                            .font(.largeTitle)
                            .foregroundColor(.gray)
                        Text("No users found")
                            .font(.headline)
                            .foregroundColor(.gray)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .listRowBackground(Color.clear)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(theme.secondaryBackgroundColor)
            .refreshable {
                do {
                    try await state.refreshSuspend()
                } catch {
                    // Handle error if needed
                }
            }
        }
    }
}

struct UserRowView: View {
    let user: UiUserV2
    @Environment(FlareTheme.self) private var theme
    @Environment(FlareRouter.self) private var router

    var body: some View {
        HStack(spacing: 12) {
            UserAvatar(data: user.avatar, size: 48)

            VStack(alignment: .leading, spacing: 4) {
                Text(user.name.raw)
                    .font(.headline)
                    .foregroundColor(theme.labelColor)
                    .lineLimit(1)

                Text(user.handle)
                    .font(.subheadline)
                    .foregroundColor(theme.labelColor.opacity(0.6))
                    .lineLimit(1)
            }

            Spacer()

            // 关注按钮
            // FollowButtonView(
            //     presenter: nil, // TODO: 需要传入适当的presenter
            //     userKey: user.key
            // )
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 16)
        .contentShape(Rectangle())
        .onTapGesture {
            let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
            router.navigate(to: .profile(
                accountType: accountType,
                userKey: user.key
            ))
        }
    }
}
