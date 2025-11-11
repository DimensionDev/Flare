import SwiftUI

struct SplashScreen: View {
    var body: some View {
        VStack {
            Image(.flareLogo)
                .resizable()
                .scaledToFit()
                .frame(width: 96, height: 96)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
