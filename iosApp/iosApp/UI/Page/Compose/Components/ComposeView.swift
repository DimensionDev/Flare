import shared
import SwiftUI

struct ComposeView: UIViewControllerRepresentable {
    let controller: UIViewController
    func makeUIViewController(context _: Context) -> UIViewController {
        controller
    }

    func updateUIViewController(_: UIViewController, context _: Context) {}
}
