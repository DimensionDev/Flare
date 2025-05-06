import shared
import SwiftUI

struct FlareMenuContainer: View {
    @State private var currentUser: UiUserV2? = nil

    @State private var accountType: AccountType = AccountTypeGuest()

    var body: some View {
        FLNewMenuView(
            accountType: accountType,
            user: currentUser
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

    private func checkAndUpdateUserState() {
        if let user = UserManager.shared.getCurrentUser() {
            currentUser = user
            accountType = AccountTypeSpecific(accountKey: user.key)
        } else {
            // No login
            accountType = AccountTypeGuest()
            currentUser = nil
        }
    }
}
