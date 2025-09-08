import SwiftUI
import KotlinSharedUI

@main
struct FlareApp: App {
    init() {
        KoinHelper.shared.start(inAppNotification: SwiftInAppNotification())
        ComposeUIHelper.shared.initialize()
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

class SwiftInAppNotification: InAppNotification {
    func onError(message: KotlinSharedUI.Message, throwable: KotlinThrowable) {
        
    }
    
    func onProgress(message: KotlinSharedUI.Message, progress: Int32, total: Int32) {
        
    }
    
    func onSuccess(message: KotlinSharedUI.Message) {
        
    }
    
    
}
