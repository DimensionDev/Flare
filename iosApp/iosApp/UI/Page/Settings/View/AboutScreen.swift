import sharedUI
import SwiftUI
import UIKit

struct AboutScreen: View {
    @Environment(\.colorScheme) var colorScheme
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        let versionName = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let versionCode = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""

        let composeBackgroundColorUInt64 = swiftUIColorToUInt64(theme.primaryBackgroundColor)

        ComposeView(
            controller: AboutViewController(
                version: "\(versionName) (\(versionCode))",
                onOpenLink: { router.handleDeepLink(.init(string: $0)!) },
                darkMode: colorScheme == .dark,
                backgroundColorValue: composeBackgroundColorUInt64
            )
        )
        .navigationTitle("settings_about_title")
        .navigationBarTitleDisplayMode(.inline)
        .scrollContentBackground(.hidden)
        .background(theme.primaryBackgroundColor)
        .scrollContentBackground(.hidden)
        .listRowBackground(theme.primaryBackgroundColor)
    }
}
