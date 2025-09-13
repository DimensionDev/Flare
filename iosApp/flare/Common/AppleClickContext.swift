import SwiftUI
import KotlinSharedUI

class AppleUriLauncher : UriLauncher {
    let openUrl: OpenURLAction
    init(openUrl: OpenURLAction) {
        self.openUrl = openUrl
    }
    
    func launch(uri: String) {
        openUrl.callAsFunction(.init(string: uri)!)
    }
}
