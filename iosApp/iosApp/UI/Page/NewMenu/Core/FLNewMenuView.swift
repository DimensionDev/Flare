import SwiftUI

struct FLNewMenuView: View {
    @Binding var isOpen: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Menu")
                .font(.title)
                .padding(.top, 50)

            Button("Close") {
                withAnimation {
                    isOpen = false
                }
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
        .background(Color(UIColor.systemBackground))
        .newSideMenuStyle()
    }
}
