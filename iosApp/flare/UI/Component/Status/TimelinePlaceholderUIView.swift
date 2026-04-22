import UIKit

/// UIKit port of `TimelinePlaceholderView` + the `UserLoadingView` row inside
/// it. SwiftUI uses `.redacted(reason: .placeholder)`; we approximate the
/// effect with flat grey bars so the cell size matches a real row.
final class TimelinePlaceholderUIView: UIView, TimelineHeightProviding {
    private static let measuredHeight: CGFloat = 44 + 12 + 96

    private let avatar: UIView = {
        let v = UIView()
        v.backgroundColor = .tertiarySystemFill
        v.layer.cornerRadius = 22
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }()
    private let nameBar = TimelinePlaceholderUIView.makeBar()
    private let handleBar = TimelinePlaceholderUIView.makeBar()
    private let body: UIView = {
        let v = UIView()
        v.backgroundColor = .tertiarySystemFill
        v.layer.cornerRadius = 6
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }()

    private static func makeBar() -> UIView {
        let v = UIView()
        v.backgroundColor = .tertiarySystemFill
        v.layer.cornerRadius = 4
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(avatar)
        addSubview(nameBar)
        addSubview(handleBar)
        addSubview(body)

        NSLayoutConstraint.activate([
            avatar.topAnchor.constraint(equalTo: topAnchor),
            avatar.leadingAnchor.constraint(equalTo: leadingAnchor),
            avatar.widthAnchor.constraint(equalToConstant: 44),
            avatar.heightAnchor.constraint(equalToConstant: 44),

            nameBar.topAnchor.constraint(equalTo: avatar.topAnchor, constant: 4),
            nameBar.leadingAnchor.constraint(equalTo: avatar.trailingAnchor, constant: 8),
            nameBar.widthAnchor.constraint(equalToConstant: 120),
            nameBar.heightAnchor.constraint(equalToConstant: 14),

            handleBar.topAnchor.constraint(equalTo: nameBar.bottomAnchor, constant: 6),
            handleBar.leadingAnchor.constraint(equalTo: nameBar.leadingAnchor),
            handleBar.widthAnchor.constraint(equalToConstant: 80),
            handleBar.heightAnchor.constraint(equalToConstant: 10),

            body.topAnchor.constraint(equalTo: avatar.bottomAnchor, constant: 12),
            body.leadingAnchor.constraint(equalTo: leadingAnchor),
            body.trailingAnchor.constraint(equalTo: trailingAnchor),
            body.bottomAnchor.constraint(equalTo: bottomAnchor),
            body.heightAnchor.constraint(equalToConstant: 96),
        ])
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        Self.measuredHeight
    }
}
