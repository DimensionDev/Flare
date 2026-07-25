#if canImport(AppKit)
import FlareUIRuntime
import AppKit

/// Renders a Flare UI tree with native AppKit views.
@MainActor
public final class FlareAppKitHostView: NSView {
    private let host: any FlareUITreeHost
    private let resources: FlareAppleResources
    private let rootStack = NSStackView()

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

    public override var intrinsicContentSize: NSSize {
        rootStack.fittingSize
    }

    isolated deinit {
        host.setOnTreeChanged(nil)
        host.dispose()
    }

    private func configureRootStack() {
        rootStack.orientation = .vertical
        rootStack.alignment = .leading
        rootStack.spacing = 0
        rootStack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(rootStack)

        NSLayoutConstraint.activate([
            rootStack.leadingAnchor.constraint(equalTo: leadingAnchor),
            rootStack.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor),
            rootStack.topAnchor.constraint(equalTo: topAnchor),
            rootStack.bottomAnchor.constraint(lessThanOrEqualTo: bottomAnchor),
        ])
    }

    private func render(_ nodes: [FlareUINode]) {
        rootStack.setViews(
            nodes.map {
                makeFlareAppKitNodeView(
                    for: $0,
                    resources: resources
                )
            },
            in: .center
        )
        invalidateIntrinsicContentSize()
    }
}
#endif
