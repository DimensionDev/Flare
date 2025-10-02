import SwiftUI
@preconcurrency import KotlinSharedUI

struct UserListScreen: View {
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<UserListPresenterState>
    private let isFollowing: Bool
    var body: some View {
        List {
            PagingView(data: presenter.state.listState) { item in
                UserCompatView(data: item)
                    .onTapGesture {
                        item.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                    }
            } loadingContent: {
                UserLoadingView()
            }

        }
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
        .navigationTitle(isFollowing ? "user_list_title_following" : "user_list_title_fans")
    }
}


extension UserListScreen {
    init(
        accountType: AccountType,
        userKey: MicroBlogKey,
        isFollowing: Bool,
    ) {
        self.isFollowing = isFollowing
        if isFollowing {
            self._presenter = .init(wrappedValue: .init(presenter: FollowingPresenter(accountType: accountType, userKey: userKey)))
        } else {
            self._presenter = .init(wrappedValue: .init(presenter: FansPresenter(accountType: accountType, userKey: userKey)))
        }
    }
}
