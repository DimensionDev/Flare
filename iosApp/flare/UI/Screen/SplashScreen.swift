import SwiftUI
import UIKit

struct SplashScreen: View {
    private var iconPreviewImageName: String {
        AppIconOption.previewImageName(for: UIApplication.shared.alternateIconName)
    }

    var body: some View {
        VStack {
            Image(iconPreviewImageName)
                .resizable()
                .scaledToFit()
                .frame(width: 96, height: 96)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
