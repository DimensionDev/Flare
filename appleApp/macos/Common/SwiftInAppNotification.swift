import Foundation
import KotlinSharedUI
import UserNotifications

final class SwiftInAppNotification: NSObject, InAppNotification, UNUserNotificationCenterDelegate {
    static let shared = SwiftInAppNotification()

    private let notificationCenter = UNUserNotificationCenter.current()
    private var progressNotifications = Set<String>()

    private override init() {
        super.init()
        notificationCenter.delegate = self
    }

    func onError(message: Message, throwable: KotlinThrowable) {
        guard shouldDeliverSystemNotification(for: message) else { return }
        progressNotifications.remove(messageKey(for: message))
        deliverNotification(
            identifier: notificationIdentifier(for: message, kind: "error"),
            title: notificationTitle(for: message, kind: .error),
            sound: .default
        )
    }

    func onProgress(message: Message, progress _: Int32, total _: Int32) {
        guard shouldDeliverSystemNotification(for: message) else { return }

        let key = messageKey(for: message)
        guard !progressNotifications.contains(key) else { return }

        progressNotifications.insert(key)
        deliverNotification(
            identifier: notificationIdentifier(for: message, kind: "progress"),
            title: notificationTitle(for: message, kind: .progress),
            sound: nil
        )
    }

    func onSuccess(message: Message) {
        guard shouldDeliverSystemNotification(for: message) else { return }
        progressNotifications.remove(messageKey(for: message))
        deliverNotification(
            identifier: notificationIdentifier(for: message, kind: "success"),
            title: notificationTitle(for: message, kind: .success),
            sound: .default
        )
    }

    func notifyProgress(
        identifier: String,
        title: String
    ) {
        guard !progressNotifications.contains(identifier) else { return }

        progressNotifications.insert(identifier)
        deliverNotification(
            identifier: notificationIdentifier(for: identifier, kind: "progress"),
            title: title,
            sound: nil
        )
    }

    func notifySuccess(
        identifier: String,
        title: String
    ) {
        progressNotifications.remove(identifier)
        deliverNotification(
            identifier: notificationIdentifier(for: identifier, kind: "success"),
            title: title,
            sound: .default
        )
    }

    func notifyError(
        identifier: String,
        title: String
    ) {
        progressNotifications.remove(identifier)
        deliverNotification(
            identifier: notificationIdentifier(for: identifier, kind: "error"),
            title: title,
            sound: .default
        )
    }

    func finishProgress(identifier: String) {
        progressNotifications.remove(identifier)
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    private func deliverNotification(
        identifier: String,
        title: String,
        sound: UNNotificationSound?
    ) {
        Task {
            guard await requestAuthorizationIfNeeded() else { return }

            let content = UNMutableNotificationContent()
            content.title = title
            content.sound = sound

            do {
                try await notificationCenter.add(
                    UNNotificationRequest(
                        identifier: identifier,
                        content: content,
                        trigger: nil
                    )
                )
            } catch {
                NSLog("Failed to deliver notification: \(error.localizedDescription)")
            }
        }
    }

    private func requestAuthorizationIfNeeded() async -> Bool {
        let settings = await notificationCenter.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional:
            return true
        case .denied:
            return false
        case .notDetermined:
            do {
                return try await notificationCenter.requestAuthorization(options: [.alert, .sound])
            } catch {
                NSLog("Failed to request notification authorization: \(error.localizedDescription)")
                return false
            }
        @unknown default:
            return false
        }
    }

    private func shouldDeliverSystemNotification(for message: Message) -> Bool {
        switch message {
        case .compose:
            true
        case .loginExpired:
            false
        }
    }

    private enum NotificationKind {
        case progress
        case success
        case error
    }

    private func notificationTitle(for message: Message, kind: NotificationKind) -> String {
        switch message {
        case .compose:
            switch kind {
            case .progress:
                String(localized: "notification_compose_progress", defaultValue: "Sending post", bundle: .main)
            case .success:
                String(localized: "notification_compose_success", defaultValue: "Post sent", bundle: .main)
            case .error:
                String(localized: "notification_compose_error", defaultValue: "Failed to send post", bundle: .main)
            }
        case .loginExpired:
            ""
        }
    }

    private func notificationIdentifier(for message: Message, kind: String) -> String {
        "dev.dimension.flare.in-app-notification.\(messageKey(for: message)).\(kind)"
    }

    private func notificationIdentifier(for identifier: String, kind: String) -> String {
        "dev.dimension.flare.in-app-notification.\(identifier).\(kind)"
    }

    private func messageKey(for message: Message) -> String {
        switch message {
        case .compose:
            "compose"
        case .loginExpired:
            "login-expired"
        }
    }
}
