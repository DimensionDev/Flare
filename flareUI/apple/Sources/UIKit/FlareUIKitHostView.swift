@preconcurrency import FlareUIDemoKit
import UIKit

/// Renders a Flare UI tree with native UIKit views.
@MainActor
public final class FlareUIKitHostView: UIView {
    private let host: FlareUiTreeHost
    private let rootStack = UIStackView()

    public init(host: FlareUiTreeHost) {
        self.host = host
        super.init(frame: .zero)
        configureRootStack()
        host.setOnTreeChanged { [weak self] nodes in
            self?.render(nodes)
        }
    }

    @available(*, unavailable)
    public required init?(coder: NSCoder) {
        fatalError("Use init(host:) instead")
    }

    deinit {
        host.setOnTreeChanged(listener: nil)
        host.dispose()
    }

    private func configureRootStack() {
        rootStack.axis = .vertical
        rootStack.alignment = .leading
        rootStack.spacing = 0
        rootStack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(rootStack)

        NSLayoutConstraint.activate([
            rootStack.leadingAnchor.constraint(equalTo: leadingAnchor),
            rootStack.trailingAnchor.constraint(equalTo: trailingAnchor),
            rootStack.topAnchor.constraint(equalTo: topAnchor),
            rootStack.bottomAnchor.constraint(lessThanOrEqualTo: bottomAnchor),
        ])
    }

    private func render(_ nodes: [FlareUiNodeSnapshot]) {
        for view in rootStack.arrangedSubviews {
            rootStack.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        for node in nodes {
            rootStack.addArrangedSubview(
                makeFlareUIKitNodeView(for: node)
            )
        }
    }
}
