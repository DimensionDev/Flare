import SwiftUI
import shared

struct AboutScreen: View {
    @Environment(\.colorScheme) var colorScheme
    @Environment(\.openURL) var openURL
    var body: some View {
        let versionName = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let versionCode = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""
        ComposeView(
            controller: AboutViewController(
                version: "\(versionName) (\(versionCode))",
                onOpenLink: { openURL(.init(string: $0)!) },
                darkMode: colorScheme == .dark
            )
        )
        .navigationTitle("about_title")
    }
}
