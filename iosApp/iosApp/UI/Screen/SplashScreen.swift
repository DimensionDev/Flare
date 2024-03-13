import SwiftUI
import shared

struct SplashScreen: View {
    var body: some View {
        Image(.logo)
            .resizable()
            .frame(width: 96, height: 96)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .clipped()
            .padding()
    }
}
