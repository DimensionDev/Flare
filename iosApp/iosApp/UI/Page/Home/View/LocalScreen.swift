import SwiftUI
import shared

struct LocalScreen: View {
    let accountType: AccountType
    
    var body: some View {
        Text("本地页面")
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
