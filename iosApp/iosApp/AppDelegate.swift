import FirebaseCore
import SwiftUI

#if os(iOS)
    typealias ApplicationDelegate = UIApplicationDelegate
#elseif os(macOS)
    typealias ApplicationDelegate = NSApplicationDelegate
#endif

class AppDelegate: NSObject, ApplicationDelegate {
    #if os(macOS)
        func applicationDidFinishLaunching(_: Notification) {
            FirebaseApp.configure()
        }
    #else
        func application(_: UIApplication,
                         didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool
        {
//      FirebaseApp.configure()
            true
        }
    #endif
}
