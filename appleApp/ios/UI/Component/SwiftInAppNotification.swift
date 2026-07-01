import FlareAppleCore
import KotlinSharedUI
import Combine
import Drops
import Foundation
import SwiftUI

struct LoginExpiredToast: Identifiable {
    let id = UUID()
    let accountKey: MicroBlogKey
    let platformType: PlatformType
}

final class SwiftInAppNotification: ObservableObject, InAppNotification {
    private init() {}
    static let shared = SwiftInAppNotification()
    @Published var loginExpiredToast: LoginExpiredToast?
    
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
                    self.loginExpiredToast = LoginExpiredToast(
                        accountKey: expiredError.accountKey,
                        platformType: expiredError.platformType
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
