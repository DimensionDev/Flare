import shared
import SwiftUI

struct MessageScreen: View {
    let accountType: AccountType

    @EnvironmentObject private var router: FlareRouter
    @EnvironmentObject private var appState: FlareAppState

    var body: some View {
        DMListView(accountType: accountType)
            .navigationTitle("Message")
            .navigationBarTitleDisplayMode(.inline)
    }
}
