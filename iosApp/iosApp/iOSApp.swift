import SwiftUI
import shared

@main
struct iOSApp: SwiftUI.App {
    init() {
        KojectHelper.shared.start()
    }
	var body: some Scene {
		WindowGroup {
            ContentView()
        }
    }
}

