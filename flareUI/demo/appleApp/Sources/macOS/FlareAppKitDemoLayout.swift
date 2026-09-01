import AppKit

@MainActor
func installFlareDemoContentView(
    _ contentView: NSView,
    in rootView: NSView
) {
    contentView.translatesAutoresizingMaskIntoConstraints = false
    rootView.addSubview(contentView)

    NSLayoutConstraint.activate([
        contentView.leadingAnchor.constraint(equalTo: rootView.leadingAnchor),
        contentView.trailingAnchor.constraint(equalTo: rootView.trailingAnchor),
        contentView.topAnchor.constraint(equalTo: rootView.topAnchor),
        contentView.bottomAnchor.constraint(equalTo: rootView.bottomAnchor),
    ])
}
