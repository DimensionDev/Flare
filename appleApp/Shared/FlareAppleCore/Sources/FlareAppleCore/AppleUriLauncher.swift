import KotlinSharedUI
import SwiftUI

public final class AppleUriLauncher: UriLauncher {
    private let openUrl: OpenURLAction

    public init(openUrl: OpenURLAction) {
        self.openUrl = openUrl
    }

    public func launch(uri: String) {
        if let url = URL(string: uri) {
            openUrl.callAsFunction(url)
        }
    }
}
