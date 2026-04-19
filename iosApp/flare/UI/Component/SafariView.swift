import Foundation
import SafariServices
import SwiftUI
import UIKit

struct SafariView: UIViewControllerRepresentable {
    var url: URL
    var onClose: () -> Void

    func makeUIViewController(context: Context) -> SFSafariViewController {
      let safariViewController = SFSafariViewController(url: url)
      safariViewController.delegate = context.coordinator
      return safariViewController
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
      Coordinator(self)
    }

    class Coordinator: NSObject, SFSafariViewControllerDelegate {
      var parent: SafariView

      init(_ safariWebView: SafariView) {
        self.parent = safariWebView
      }

      func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
        parent.onClose()
      }
    }
}
