#if canImport(UIKit)
import FlareUIRuntime
import UIKit

/// Renders a Flare UI tree with native UIKit views.
@MainActor
public final class FlareUIKitHostView: UIView {
    private let host: any FlareUITreeHost
    private let resources: FlareAppleResources
    private let rootStack = UIStackView()

    public init(
        host: any FlareUITreeHost,
        resources: FlareAppleResources = .init(bundle: .main)
    ) {
        self.host = host
        self.resources = resources
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

    isolated deinit {
        host.setOnTreeChanged(nil)
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

    private func render(_ nodes: [FlareUINode]) {
        for view in rootStack.arrangedSubviews {
            rootStack.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        for node in nodes {
            rootStack.addArrangedSubview(
                makeFlareUIKitNodeView(
                    for: node,
                    resources: resources
                )
            )
        }
    }
}
#endif
