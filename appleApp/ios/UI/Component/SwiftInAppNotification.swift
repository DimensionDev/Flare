import FlareAppleCore
import KotlinSharedUI
import Drops
import Foundation
import SwiftUI

class SwiftInAppNotification: InAppNotification {
    private init() {}
    static let shared = SwiftInAppNotification()
    
    func onError(message: Message, throwable: KotlinThrowable) {
        DispatchQueue.main.async {
            switch message {
            case .compose:
                Drops.show(
                    .init(
                        title: .init(localized: "notification_compose_error"),
                        icon: UIImage(fontAwesome: .circleExclamation)

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
    }

    func onProgress(message: Message, progress: Int32, total: Int32) {
        
    }

    func onSuccess(message: Message) {
        DispatchQueue.main.async {
            switch message {
            case .compose:
                Drops.show(
                    .init(
                        title: .init(localized: "notification_compose_success"),
                        icon: UIImage(fontAwesome: .circleCheck)
                    )
                )
            case .loginExpired:
                // do nothing
                break
            }
        }
    }
}
