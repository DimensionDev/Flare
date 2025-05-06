import FirebaseCore
import SwiftUI
import Tiercel
import WishKit

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
            WishKit.configure(with: "8B16D016-FC6D-4DEB-8FC5-91E6A7C46A5C")
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
