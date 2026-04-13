import SwiftUI
import KotlinSharedUI

class AppleUriLauncher: UriLauncher {
    let openUrl: OpenURLAction
    init(openUrl: OpenURLAction) {
        self.openUrl = openUrl
    }

    func launch(uri: String) {
        if let url = URL(string: uri) {
            openUrl.callAsFunction(url)
        }
    }
}
