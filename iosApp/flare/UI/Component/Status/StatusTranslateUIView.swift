import UIKit
import SwiftUI
import KotlinSharedUI

/// Wrapper around the existing SwiftUI `StatusTranslateView`.
///
/// StatusTranslateView drives two Kotlin presenters (`TranslatePresenter`,
/// `AiTLDRPresenter`) via `@StateObject KotlinPresenter<UiState<...>>` +
/// `StateView` — the presenter lifecycle is tied to SwiftUI's view identity.
/// Re-implementing that glue in UIKit would require wiring `KotlinPresenter`
/// (Combine `ObservableObject`) into a UIKit observer here; for now, the
/// leaf is bridged the same way as `RichTextUIView`.
final class StatusTranslateUIView: UIView {
    var content: UiRichText? { didSet { update() } }
    var contentWarning: UiRichText? { didSet { update() } }

    private let host: UIHostingController<AnyView>

    override init(frame: CGRect) {
        self.host = UIHostingController(rootView: AnyView(EmptyView()))
        super.init(frame: frame)
        commonInit()
    }
    required init?(coder: NSCoder) {
        self.host = UIHostingController(rootView: AnyView(EmptyView()))
        super.init(coder: coder)
        commonInit()
    }

    private func commonInit() {
        host.view.backgroundColor = .clear
        host.view.translatesAutoresizingMaskIntoConstraints = false
        addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.topAnchor.constraint(equalTo: topAnchor),
            host.view.leadingAnchor.constraint(equalTo: leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: trailingAnchor),
            host.view.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window != nil, host.parent == nil, let parent = findParentViewController() else { return }
        parent.addChild(host)
        host.didMove(toParent: parent)
    }

    private func update() {
        guard let content = content else {
            host.rootView = AnyView(EmptyView())
            return
        }
        host.rootView = AnyView(
            StatusTranslateView(content: content, contentWarning: contentWarning)
        )
        invalidateIntrinsicContentSize()
    }

    private func findParentViewController() -> UIViewController? {
        var r: UIResponder? = self
        while let n = r { if let vc = n as? UIViewController { return vc }; r = n.next }
        return nil
    }
}
