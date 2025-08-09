import shared
import SwiftUI

struct MessageScreen: View {
    let accountType: AccountType

    @Environment(FlareRouter.self) private var router
    @Environment(FlareMenuState.self) private var menuState

    var body: some View {
        DMListView(accountType: accountType)
            .navigationTitle("Message")
            .navigationBarTitleDisplayMode(.inline)
    }
}
