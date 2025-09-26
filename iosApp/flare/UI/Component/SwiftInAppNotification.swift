import KotlinSharedUI
import Drops
import Foundation

class SwiftInAppNotification: InAppNotification {
    private init() {}
    static let shared = SwiftInAppNotification()
    
    func onError(message: Message, throwable: KotlinThrowable) {
        switch message {
        case .compose:
            Drops.show(.init(stringLiteral: .init(localized: "notification_compose_error")))
        case .loginExpired:
            Drops.show(.init(stringLiteral: .init(localized: "notification_login_expired")))
        }
    }

    func onProgress(message: Message, progress: Int32, total: Int32) {

    }

    func onSuccess(message: Message) {
        switch message {
        case .compose:
            Drops.show(.init(stringLiteral: .init(localized: "notification_compose_success")))
        case .loginExpired:
            // do nothing
            break
        }
    }
}
