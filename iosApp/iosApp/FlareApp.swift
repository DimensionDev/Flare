import SwiftUI
import shared

@main
struct FlareApp: SwiftUI.App {
    init() {
        KoinHelper.shared.start()
    }
    
    var body: some Scene {
        WindowGroup {
            RouterView()
        }
    }
}

