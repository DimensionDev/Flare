import SwiftUI
import KotlinSharedUI

@main
struct FlareApp: App {
    init() {
        ComposeUIHelper.shared.initialize(inAppNotification: SwiftInAppNotification())
    }
    var body: some Scene {
        WindowGroup {
            FlareRoot()
        }
    }
}

class SwiftInAppNotification: InAppNotification {
    func onError(message: Message, throwable: KotlinThrowable) {
        
    }
    
    func onProgress(message: Message, progress: Int32, total: Int32) {
        
    }
    
    func onSuccess(message: Message) {
        
    }
}
