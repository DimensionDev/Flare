import KotlinSharedUI
import Drops
import Foundation
import SwiftUI

class SwiftInAppNotification: InAppNotification {
    private init() {}
    static let shared = SwiftInAppNotification()
    
    func onError(message: Message, throwable: KotlinThrowable) {
        switch message {
        case .compose:
            Drops.show(
                .init(
                    title: .init(localized: "notification_compose_error"),
                    icon: .faCircleExclamation

                )
            )
        case .loginExpired:
            if let expiredError = throwable as? LoginExpiredException {
                Drops.show(
                    .init(
                        title: .init(localized: "notification_login_expired"),
                        subtitle: .init(localized: "error_login_expired \(expiredError.accountKey)"),
                        icon: UIImage(systemName: "person.badge.shield.exclamationmark")
                    )
                )
            } else {
                Drops.show(.init(stringLiteral: .init(localized: "notification_login_expired")))
            }
        }
    }

    func onProgress(message: Message, progress: Int32, total: Int32) {

    }

    func onSuccess(message: Message) {
        switch message {
        case .compose:
            Drops.show(
                .init(
                    title: .init(localized: "notification_compose_success"),
                    icon: .faCircleCheck
                )
            )
        case .loginExpired:
            // do nothing
            break
        }
    }
}
