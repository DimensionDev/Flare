import SwiftUI
import KotlinSharedUI

@main
struct FlareApp: App {
    init() {
        ComposeUIHelper.shared.initialize(
            inAppNotification: SwiftInAppNotification.shared,
        )
    }
    var body: some Scene {
        WindowGroup {
            FlareTheme {
                if #available(iOS 18.0, *) {
                    FlareRoot()
                } else {
                    BackportFlareRoot()
                }
            }
        }
    }
}
