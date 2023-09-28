import SwiftUI
import shared

@main
struct FlareApp: SwiftUI.App {
    init() {
        KojectHelper.shared.start()
    }
    
    var body: some Scene {
        WindowGroup {
            RouterView()
        }
    }
}

