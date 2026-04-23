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
    private var lastReportedSize: CGSize = .zero
    private var isLayoutInvalidationScheduled = false

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
        clipsToBounds = true
        host.view.backgroundColor = .clear
        host.view.translatesAutoresizingMaskIntoConstraints = false
        host.sizingOptions = [.intrinsicContentSize]
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
            lastReportedSize = .zero
            host.rootView = AnyView(EmptyView())
            return
        }
        host.rootView = AnyView(
            StatusTranslateView(
                content: content,
                contentWarning: contentWarning,
                onSizeChange: { [weak self] size in
                    self?.handleSizeChange(size)
                },
                onLayoutChange: { [weak self] in
                    self?.scheduleContainingLayoutInvalidation()
                }
            )
        )
        host.view.invalidateIntrinsicContentSize()
        invalidateIntrinsicContentSize()
    }

    private func handleSizeChange(_ size: CGSize) {
        guard size.width.isFinite, size.height.isFinite else { return }
        let didChange =
            abs(size.width - lastReportedSize.width) > 0.5 ||
            abs(size.height - lastReportedSize.height) > 0.5
        guard didChange else { return }
        lastReportedSize = size
        scheduleContainingLayoutInvalidation()
    }

    private func scheduleContainingLayoutInvalidation() {
        guard !isLayoutInvalidationScheduled else { return }
        isLayoutInvalidationScheduled = true
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.isLayoutInvalidationScheduled = false
            self.host.view.invalidateIntrinsicContentSize()
            self.invalidateIntrinsicContentSize()
            self.setNeedsLayout()
            self.superview?.setNeedsLayout()
            self.invalidateContainingCollectionLayout()
        }
    }

    private func invalidateContainingCollectionLayout() {
        var responder: UIResponder? = self
        var cellRef: UICollectionViewCell?
        while let current = responder {
            if cellRef == nil, let cell = current as? UICollectionViewCell {
                cellRef = cell
            }
            if let collectionView = current as? UICollectionView {
                if let cell = cellRef, let indexPath = collectionView.indexPath(for: cell) {
                    cell.setNeedsLayout()
                    cell.contentView.setNeedsLayout()
                    cell.layoutIfNeeded()
                    let context = UICollectionViewLayoutInvalidationContext()
                    context.invalidateItems(at: [indexPath])
                    collectionView.collectionViewLayout.invalidateLayout(with: context)
                } else {
                    collectionView.collectionViewLayout.invalidateLayout()
                }
                collectionView.performBatchUpdates(nil)
                return
            }
            responder = current.next
        }
    }

    private func findParentViewController() -> UIViewController? {
        var r: UIResponder? = self
        while let n = r { if let vc = n as? UIViewController { return vc }; r = n.next }
        return nil
    }
}
