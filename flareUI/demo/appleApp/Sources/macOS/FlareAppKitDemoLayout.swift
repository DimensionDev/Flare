import AppKit

@MainActor
func installFlareDemoContentView(
    _ contentView: NSView,
    in rootView: NSView
) {
    contentView.translatesAutoresizingMaskIntoConstraints = false
    rootView.addSubview(contentView)

    NSLayoutConstraint.activate([
        contentView.leadingAnchor.constraint(equalTo: rootView.leadingAnchor, constant: 24),
        contentView.trailingAnchor.constraint(equalTo: rootView.trailingAnchor, constant: -24),
        contentView.topAnchor.constraint(equalTo: rootView.topAnchor, constant: 24),
        contentView.bottomAnchor.constraint(
            lessThanOrEqualTo: rootView.bottomAnchor,
            constant: -24
        ),
    ])
}
