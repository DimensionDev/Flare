import SwiftUI

struct MenuView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text("Profile")
            Text("Settings")
            Spacer()
        }
        .padding(.top, 60)
        .padding(.horizontal)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(.regularMaterial)
    }
} 