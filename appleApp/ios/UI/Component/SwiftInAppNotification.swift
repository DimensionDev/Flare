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
    var onRelogin: ((LoginExpiredToast) -> Void)?
    
    func onError(message: Message, throwable: KotlinThrowable) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
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
                    let toast = LoginExpiredToast(
                        accountKey: expiredError.accountKey,
                        platformType: expiredError.platformType
                    )
                    Drops.show(
                        .init(
                            title: String(localized: "login_expired", defaultValue: "Login session expired"),
                            subtitle: "\(expiredError.accountKey)",
                            subtitleNumberOfLines: 1,
                            icon: UIImage(fontAwesome: .circleExclamation),
                            action: .init(icon: UIImage(fontAwesome: .arrowsRotate)) { [weak self] in
                                Drops.hideCurrent()
                                self?.onRelogin?(toast)
                            },
                            duration: .seconds(5)
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
