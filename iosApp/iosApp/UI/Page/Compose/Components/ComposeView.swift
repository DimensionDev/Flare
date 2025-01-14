import shared
import SwiftUI

struct ComposeView: UIViewControllerRepresentable {
    let controller: UIViewController
    func makeUIViewController(context: Context) -> UIViewController {
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}
