import FirebaseAnalytics
import FirebaseCore
import FirebaseCrashlytics
import Foundation

enum FirebaseBootstrap {
    @discardableResult
    static func configureIfAvailable() -> Bool {
        guard FirebaseApp.app() == nil else {
            return true
        }
        guard
            let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
            let options = FirebaseOptions(contentsOfFile: path)
        else {
            return false
        }

        FirebaseApp.configure(options: options)
        Analytics.setAnalyticsCollectionEnabled(true)
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(true)
        return true
    }
}
