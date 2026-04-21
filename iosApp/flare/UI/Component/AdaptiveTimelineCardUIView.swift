import UIKit
import KotlinSharedUI

/// UIKit port of `AdaptiveTimelineCard` + `ListCardView`.
///
/// Mirrors the SwiftUI branching, with UIKit collection layouts owning
/// outer screen spacing:
///   if isMultipleColumn || !(timelineDisplayMode == .plain) {
///       ListCardView(...) { content }
///   } else {
///       VStack(spacing: 0) { content; if !last { Divider() } }
///   }
final class AdaptiveTimelineCardUIView: UIView {

    // Mirrors `@Environment(\.appearanceSettings.timelineDisplayMode == .plain)`.
    var isPlainTimelineDisplayMode = true
    // Mirrors `@Environment(\.isMultipleColumn)`.
    var isMultipleColumn: Bool = false

    var index: Int = 0
    var totalCount: Int = 0

    private static let cornerRadius: CGFloat = 32
    private static let plainEdgeRadius: CGFloat = 4

    private let contentContainer = UIView()
    private let cardBackground = CAShapeLayer()
    private let divider: UIView = {
        let v = UIView()
        v.backgroundColor = .separator
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }()

    private var leadingConstraint: NSLayoutConstraint!
    private var trailingConstraint: NSLayoutConstraint!
    private var dividerHeightConstraint: NSLayoutConstraint!
    private var contentBottomToDividerConstraint: NSLayoutConstraint!
    private var contentBottomToBottomConstraint: NSLayoutConstraint!
    private weak var contentView: UIView?

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentContainer.translatesAutoresizingMaskIntoConstraints = false
        addSubview(contentContainer)

        leadingConstraint = contentContainer.leadingAnchor.constraint(equalTo: leadingAnchor)
        trailingConstraint = contentContainer.trailingAnchor.constraint(equalTo: trailingAnchor)
        NSLayoutConstraint.activate([
            contentContainer.topAnchor.constraint(equalTo: topAnchor),
            leadingConstraint,
            trailingConstraint,
        ])

        addSubview(divider)
        dividerHeightConstraint = divider.heightAnchor.constraint(equalToConstant: 1.0 / UIScreen.main.scale)
        contentBottomToDividerConstraint = contentContainer.bottomAnchor.constraint(equalTo: divider.topAnchor)
        contentBottomToBottomConstraint = contentContainer.bottomAnchor.constraint(equalTo: bottomAnchor)
        NSLayoutConstraint.activate([
            divider.leadingAnchor.constraint(equalTo: leadingAnchor),
            divider.trailingAnchor.constraint(equalTo: trailingAnchor),
            divider.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        layer.insertSublayer(cardBackground, at: 0)
        cardBackground.fillColor = UIColor.secondarySystemGroupedBackground.cgColor
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    /// Swaps in a fresh content subview (replacing any prior one).
    func setContent(_ view: UIView) {
        contentContainer.subviews.forEach { $0.removeFromSuperview() }
        contentView = view
        view.translatesAutoresizingMaskIntoConstraints = false
        contentContainer.addSubview(view)
        NSLayoutConstraint.activate([
            view.topAnchor.constraint(equalTo: contentContainer.topAnchor),
            view.leadingAnchor.constraint(equalTo: contentContainer.leadingAnchor),
            view.trailingAnchor.constraint(equalTo: contentContainer.trailingAnchor),
            view.bottomAnchor.constraint(equalTo: contentContainer.bottomAnchor),
        ])
    }

    func configure(index: Int, totalCount: Int) {
        self.index = index
        self.totalCount = totalCount
        applyMode()
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        cardBackground.fillColor = UIColor.secondarySystemGroupedBackground.cgColor
    }

    private var useCardStyle: Bool {
        isMultipleColumn || !isPlainTimelineDisplayMode
    }

    private func applyMode() {
        if useCardStyle {
            // Screen spacing is owned by the collection layout so cell widths
            // and inter-column spacing stay consistent.
            leadingConstraint.constant = 0
            trailingConstraint.constant = 0
            cardBackground.isHidden = false
            divider.isHidden = true
            dividerHeightConstraint.isActive = false
            contentBottomToDividerConstraint.isActive = false
            contentBottomToBottomConstraint.isActive = true
        } else {
            // Plain mode: no horizontal padding, divider after non-last items.
            leadingConstraint.constant = 0
            trailingConstraint.constant = 0
            cardBackground.isHidden = true
            let isLast = !(totalCount <= 0 || index < totalCount - 1)
            divider.isHidden = isLast
            dividerHeightConstraint.isActive = !isLast
            contentBottomToBottomConstraint.isActive = isLast
            contentBottomToDividerConstraint.isActive = !isLast
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if useCardStyle {
            cardBackground.path = makeCardPath().cgPath
            cardBackground.frame = bounds
        }
    }

    /// Uneven rounded rectangle matching SwiftUI's `UnevenRoundedRectangle`:
    /// first/last items keep big corners on their outer edges, interior edges
    /// shrink to a 4pt radius for the tight-stacked look.
    private func makeCardPath() -> UIBezierPath {
        let rect = contentContainer.frame
        let (tl, tr, br, bl): (CGFloat, CGFloat, CGFloat, CGFloat)
        if isMultipleColumn {
            tl = Self.cornerRadius; tr = Self.cornerRadius
            br = Self.cornerRadius; bl = Self.cornerRadius
        } else {
            let top = index == 0 ? Self.cornerRadius : Self.plainEdgeRadius
            let bottom = index == totalCount - 1 ? Self.cornerRadius : Self.plainEdgeRadius
            tl = top; tr = top
            br = bottom; bl = bottom
        }
        return UIBezierPath.roundedRect(rect: rect, topLeft: tl, topRight: tr, bottomRight: br, bottomLeft: bl)
    }
}

private extension UIBezierPath {
    static func roundedRect(rect: CGRect, topLeft: CGFloat, topRight: CGFloat, bottomRight: CGFloat, bottomLeft: CGFloat) -> UIBezierPath {
        let p = UIBezierPath()
        let minX = rect.minX, maxX = rect.maxX, minY = rect.minY, maxY = rect.maxY
        p.move(to: CGPoint(x: minX + topLeft, y: minY))
        p.addLine(to: CGPoint(x: maxX - topRight, y: minY))
        p.addArc(withCenter: CGPoint(x: maxX - topRight, y: minY + topRight),
                 radius: topRight, startAngle: -.pi / 2, endAngle: 0, clockwise: true)
        p.addLine(to: CGPoint(x: maxX, y: maxY - bottomRight))
        p.addArc(withCenter: CGPoint(x: maxX - bottomRight, y: maxY - bottomRight),
                 radius: bottomRight, startAngle: 0, endAngle: .pi / 2, clockwise: true)
        p.addLine(to: CGPoint(x: minX + bottomLeft, y: maxY))
        p.addArc(withCenter: CGPoint(x: minX + bottomLeft, y: maxY - bottomLeft),
                 radius: bottomLeft, startAngle: .pi / 2, endAngle: .pi, clockwise: true)
        p.addLine(to: CGPoint(x: minX, y: minY + topLeft))
        p.addArc(withCenter: CGPoint(x: minX + topLeft, y: minY + topLeft),
                 radius: topLeft, startAngle: .pi, endAngle: 3 * .pi / 2, clockwise: true)
        p.close()
        return p
    }
}
