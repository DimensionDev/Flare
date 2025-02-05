import shared
import SwiftUI

class AppleUriLauncher: UriLauncher {
    private let openURL: OpenURLAction

    init(openURL: OpenURLAction) {
        self.openURL = openURL
    }

    func launch(uri: String) {
        guard let url = URL(string: uri) else { return }
        openURL(url)
    }
}
