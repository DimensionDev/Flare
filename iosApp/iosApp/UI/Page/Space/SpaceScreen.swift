import shared
import SwiftUI

struct SpaceScreen: View {
    let accountType: AccountType

    var body: some View {
        VStack {
            Text("Spaces")
                .font(.largeTitle)
                .padding()

            Text("Coming soon...")
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .navigationTitle("Spaces")
        .navigationBarTitleDisplayMode(.inline)
    }
}
