import SwiftUI
import UIKit

struct ActivityView: UIViewControllerRepresentable {
    let activityItems: [Any]
    let applicationActivities: [UIActivity]? = nil
    @Environment(\.dismiss) var dismiss

    func makeUIViewController(context _: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(
            activityItems: activityItems,
            applicationActivities: applicationActivities
        )
        // 设置完成回调，以便在分享完成后自动关闭
        controller.completionWithItemsHandler = { _, _, _, _ in
            dismiss()
        }
        return controller
    }

    func updateUIViewController(_: UIActivityViewController, context _: Context) {}
}
