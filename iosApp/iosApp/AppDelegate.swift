import FirebaseCore
import SwiftUI
import Tiercel

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
            // DownloadManager初始化
            _ = DownloadManager.shared
            return true
        }

        // 处理后台下载
        func application(_: UIApplication,
                         handleEventsForBackgroundURLSession identifier: String,
                         completionHandler: @escaping () -> Void)
        {
            if identifier == DownloadManager.shared.sessionManager.identifier {
                DownloadManager.shared.setCompletionHandler(completionHandler)
            }
        }
    #endif
}
