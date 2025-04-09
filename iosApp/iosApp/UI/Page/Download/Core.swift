import Foundation

extension Notification.Name {
    static let downloadProgressUpdated = Notification.Name("downloadProgressUpdated")
    static let downloadCompleted = Notification.Name("downloadCompleted")
    static let downloadTaskDidSucceed = Notification.Name("downloadTaskDidSucceed")
    static let downloadFailed = Notification.Name("downloadFailed")
}
