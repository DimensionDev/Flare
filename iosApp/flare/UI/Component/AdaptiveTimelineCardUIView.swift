import UIKit
import KotlinSharedUI

/// UIKit port of `AdaptiveTimelineCard` + `ListCardView`.
/// Manual frame-based layout — no Auto Layout constraints.
final class AdaptiveTimelineCardUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {

    var isPlainTimelineDisplayMode = true
    var isMultipleColumn: Bool = false

    var index: Int = 0
    var totalCount: Int = 0

    private static let cornerRadius: CGFloat = 32
    private static let plainEdgeRadius: CGFloat = 4
    private static let dividerHeight: CGFloat = 1.0 / UIScreen.main.scale

    private let contentContainer = UIView()
    private let cardBackground = CAShapeLayer()
    private let divider: UIView = {
        let v = UIView()
        v.backgroundColor = .separator
        return v
    }()

    private weak var contentView: UIView?
    private var showsDivider: Bool = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(contentContainer)
        addSubview(divider)
        layer.insertSublayer(cardBackground, at: 0)
        cardBackground.fillColor = UIColor.secondarySystemGroupedBackground.cgColor
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func setContent(_ view: UIView) {
        contentContainer.subviews.forEach { $0.removeFromSuperview() }
        contentView = view
        contentContainer.addSubview(view)
        setNeedsLayout()
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
            cardBackground.isHidden = false
            divider.isHidden = true
            showsDivider = false
        } else {
            cardBackground.isHidden = true
            let isLast = !(totalCount <= 0 || index < totalCount - 1)
            divider.isHidden = isLast
            showsDivider = !isLast
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width
        let h = bounds.height

        if showsDivider {
            let dh = Self.dividerHeight
            let containerH = max(h - dh, 0)
            contentContainer.frame = CGRect(x: 0, y: 0, width: w, height: containerH)
            divider.frame = CGRect(x: 0, y: containerH, width: w, height: dh)
        } else {
            contentContainer.frame = CGRect(x: 0, y: 0, width: w, height: h)
        }

        // Layout content view to fill contentContainer
        contentView?.frame = contentContainer.bounds

        if useCardStyle {
            cardBackground.path = makeCardPath().cgPath
            cardBackground.frame = bounds
        }
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        let contentH: CGFloat
        if let cv = contentView {
            contentH = childHeight(of: cv, for: width)
        } else {
            contentH = 0
        }
        let dividerH = showsDivider ? Self.dividerHeight : 0
        return ceil(contentH + dividerH)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(CGSize(width: targetSize.width, height: .greatestFiniteMagnitude))
    }

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
