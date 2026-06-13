import AppKit

final class AdaptiveTimelineCardNSView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    var isPlainTimelineDisplayMode = true
    var isMultipleColumn = false
    var index = 0
    var totalCount = 0

    private static let cornerRadius: CGFloat = 18
    private static let plainEdgeRadius: CGFloat = 4
    private static let dividerHeight: CGFloat = 1

    private let contentContainer = FlippedView()
    private let divider = FlippedView()
    private weak var contentView: NSView?
    private var showsDivider = false

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        contentContainer.wantsLayer = true
        divider.wantsLayer = true
        divider.layer?.backgroundColor = NSColor.separatorColor.cgColor
        addSubview(contentContainer)
        addSubview(divider)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func setContent(_ view: NSView) {
        contentContainer.subviews.forEach { $0.removeFromSuperview() }
        contentView = view
        contentContainer.addSubview(view)
        needsLayout = true
    }

    func configure(index: Int, totalCount: Int) {
        self.index = index
        self.totalCount = totalCount
        applyMode()
        needsLayout = true
    }

    override func viewDidChangeEffectiveAppearance() {
        super.viewDidChangeEffectiveAppearance()
        layer?.backgroundColor = cardBackgroundColor.cgColor
        divider.layer?.backgroundColor = NSColor.separatorColor.cgColor
    }

    override func layout() {
        super.layout()
        let width = bounds.width
        let height = bounds.height

        if showsDivider {
            let containerHeight = max(height - Self.dividerHeight, 0)
            contentContainer.frame = CGRect(x: 0, y: 0, width: width, height: containerHeight)
            divider.frame = CGRect(x: 0, y: containerHeight, width: width, height: Self.dividerHeight)
        } else {
            contentContainer.frame = bounds
            divider.frame = .zero
        }

        contentView?.frame = contentContainer.bounds
        applyCardShape()
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        let contentHeight = contentView.map { childHeight(of: $0, for: width) } ?? 0
        let dividerHeight = showsDivider ? Self.dividerHeight : 0
        return ceil(contentHeight + dividerHeight)
    }

    private var useCardStyle: Bool {
        isMultipleColumn || !isPlainTimelineDisplayMode
    }

    private var cardBackgroundColor: NSColor {
        NSColor.controlBackgroundColor
    }

    private func applyMode() {
        if useCardStyle {
            layer?.backgroundColor = cardBackgroundColor.cgColor
            divider.isHidden = true
            showsDivider = false
        } else {
            layer?.backgroundColor = NSColor.clear.cgColor
            let isLast = !(totalCount <= 0 || index < totalCount - 1)
            divider.isHidden = isLast
            showsDivider = !isLast
        }
    }

    private func applyCardShape() {
        guard useCardStyle else {
            layer?.cornerRadius = 0
            return
        }
        if isMultipleColumn {
            layer?.cornerRadius = Self.cornerRadius
        } else {
            let topRadius = index == 0 ? Self.cornerRadius : Self.plainEdgeRadius
            let bottomRadius = index == totalCount - 1 ? Self.cornerRadius : Self.plainEdgeRadius
            layer?.cornerRadius = max(topRadius, bottomRadius)
        }
        layer?.maskedCorners = [
            .layerMinXMinYCorner,
            .layerMaxXMinYCorner,
            .layerMinXMaxYCorner,
            .layerMaxXMaxYCorner,
        ]
    }
}
