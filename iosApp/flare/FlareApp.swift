import SwiftUI
import KotlinSharedUI

@main
struct FlareApp: App {
    init() {
        ComposeUIHelper.shared.initialize(
            inAppNotification: SwiftInAppNotification.shared,
            appleWebScraper: AppleScraper.shared
        )
    }
    var body: some Scene {
        WindowGroup {
            FlareTheme {
                FlareRoot()
            }
        }
    }
}
