import Foundation
import SwiftUI

 private struct VersionBannerKey: EnvironmentKey {
    static let defaultValue: Bool = false
}

extension EnvironmentValues {
    var shouldShowVersionBanner: Bool {
        get { self[VersionBannerKey.self] }
        set { self[VersionBannerKey.self] = newValue }
    }
}

 
extension Notification.Name {
 
    static let versionBannerDismissed = Notification.Name("versionBannerDismissed")
}
