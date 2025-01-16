import shared
import SwiftUI

struct PublicScreen: View {
    let accountType: AccountType

    var body: some View {
        Text("公开页面")
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
