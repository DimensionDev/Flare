import Foundation
import KotlinSharedUI

final class SwiftInAppNotification: InAppNotification {
    private init() {}

    static let shared = SwiftInAppNotification()

    func onError(message: Message, throwable: KotlinThrowable) {
    }

    func onProgress(message: Message, progress: Int32, total: Int32) {
    }

    func onSuccess(message: Message) {
    }
}
