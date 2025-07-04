import shared
import SwiftUI

struct MessageScreen: View {
    let accountType: AccountType

    @Environment(FlareRouter.self) private var router
    @Environment(FlareAppState.self) private var appState

    var body: some View {
        DMListView(accountType: accountType)
            .navigationTitle("Message")
            .navigationBarTitleDisplayMode(.inline)
    }
}
