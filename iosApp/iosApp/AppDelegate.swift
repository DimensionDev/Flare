import SwiftUI
import FirebaseCore
#if os(iOS)
typealias ApplicationDelegate = UIApplicationDelegate
#elseif os(macOS)
typealias ApplicationDelegate = NSApplicationDelegate
#endif

class AppDelegate: NSObject, ApplicationDelegate {
    #if os(macOS)
    func applicationDidFinishLaunching(_ notification: Notification) {
        FirebaseApp.configure()
    }
    #else
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
//      FirebaseApp.configure()

      return true
    }
    #endif
}
