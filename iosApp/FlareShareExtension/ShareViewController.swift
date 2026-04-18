import UIKit
import Social
import FlareUI
import KotlinSharedUI
import SwiftUI
import UniformTypeIdentifiers

class ShareViewController: UIViewController {
    private static var didInitKoin = false
    override func viewDidLoad() {
        super.viewDidLoad()
        if !Self.didInitKoin {
            Self.didInitKoin = true
            ComposeUIHelper.shared.initializeLite()
        }
        // Extract shared data (e.g., URL or Text)
        guard let extensionItem = extensionContext?.inputItems.first as? NSExtensionItem,
              let itemProvider = extensionItem.attachments else {
            self.extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
            return
        }
        
        // Host the SwiftUI View
        let contentView = UIHostingController(
            rootView: ComposeView(itemProvider: itemProvider, dismiss: {
                self.extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
            })
        )
        self.addChild(contentView)
        self.view.addSubview(contentView.view)
        contentView.view.frame = self.view.bounds
    }
}

struct ComposeView: View {
    let itemProvider: [NSItemProvider]
    let dismiss: () -> Void
    @ObservedObject private var presenter = KotlinPresenter(presenter: ComposePresenter(accountType: nil))
    
    var body: some View {
        NavigationStack {
            ComposeContent(itemProvider: itemProvider, state: presenter.state, dismiss: dismiss) { _ in
                EmptyView()
            }
        }
    }
}

class EmptyInAppNotification: InAppNotification {
    func onSuccess(message: Message) {
        
    }
    func onError(message: Message, throwable: KotlinThrowable) {
        
    }
    func onProgress(message: Message, progress: Int32, total: Int32) {
        
    }
}
