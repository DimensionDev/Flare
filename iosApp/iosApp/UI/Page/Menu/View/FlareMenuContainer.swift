import shared
import SwiftUI

struct FlareMenuContainer<Content: View>: View {
    let content: Content

    @ObservedObject var appState: FlareAppState

    @ObservedObject var router: FlareRouter

    @State private var currentUser: UiUserV2? = nil

    @State private var accountType: AccountType = AccountTypeGuest()

    init(content: Content, appState: FlareAppState, router: FlareRouter) {
        self.content = content
        self.appState = appState
        self.router = router
    }

    var body: some View {
        FLNewSideMenu(
            isOpen: $appState.isMenuOpen,
            menu: menuView,
            content: contentView
        )
        .onAppear {
            checkAndUpdateUserState()
        }
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("UserAccountDidChange"))) { _ in

            checkAndUpdateUserState()
        }
        .onReceive(NotificationCenter.default.publisher(for: .userDidUpdate)) { notification in
            if let user = notification.object as? UiUserV2 {
                currentUser = user
                accountType = AccountTypeSpecific(accountKey: user.key)
            }
        }
    }

    private var menuView: some View {
        FLNewMenuView(
            isOpen: $appState.isMenuOpen,
            accountType: accountType,
            user: currentUser
        )
        .environmentObject(router)
    }

    private var contentView: some View {
        content
            .flareNavigationGesture(router: router)
    }

    private func checkAndUpdateUserState() {
        if let user = UserManager.shared.getCurrentUser() {
            currentUser = user
            accountType = AccountTypeSpecific(accountKey: user.key)
        } else {
            // 未登录状态
            accountType = AccountTypeGuest()
            currentUser = nil
        }
    }
}
