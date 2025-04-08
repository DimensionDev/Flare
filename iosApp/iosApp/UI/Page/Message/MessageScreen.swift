import shared
import SwiftUI

struct MessageScreen: View {
    let accountType: AccountType

    var body: some View {
        VStack {
            Text("Messages")
                .font(.largeTitle)
                .padding()

            Text("Coming soon...")
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
