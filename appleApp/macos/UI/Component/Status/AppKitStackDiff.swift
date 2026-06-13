import AppKit

protocol ManualLayoutMeasurable: AnyObject {}

protocol TimelineHeightProviding: AnyObject {
    func timelineHeight(for width: CGFloat) -> CGFloat?
}

class FlippedView: NSView {
    override var isFlipped: Bool { true }
}

func syncManagedSubviews(parent: NSView, current: inout [NSView], desired: [NSView]) {
    if current.count == desired.count,
       zip(current, desired).allSatisfy({ $0 === $1 }) {
        for view in desired where view.superview !== parent {
            parent.addSubview(view)
        }
        return
    }

    let desiredIDs = Set(desired.map { ObjectIdentifier($0) })
    for view in current where !desiredIDs.contains(ObjectIdentifier(view)) {
        view.removeFromSuperview()
    }
    current = desired
    for view in desired where view.superview !== parent {
        parent.addSubview(view)
    }
}

func childHeight(of view: NSView, for width: CGFloat) -> CGFloat {
    guard width > 0, width.isFinite else { return 0 }

    if let provider = view as? TimelineHeightProviding,
       let height = provider.timelineHeight(for: width),
       isValidFittingLength(height) {
        return ceil(height)
    }

    if let text = view as? TimelineTextField {
        return ceil(text.timelineHeight(for: width) ?? 0)
    }

    let fitting = view.fittingSize
    if isValidFittingLength(fitting.height) {
        return ceil(fitting.height)
    }

    return 0
}

func childWidth(of view: NSView, for height: CGFloat) -> CGFloat {
    if let text = view as? TimelineTextField {
        return ceil(text.preferredWidth(for: height))
    }

    let fitting = view.fittingSize
    if fitting.width > 0, fitting.width.isFinite {
        return ceil(fitting.width)
    }
    return 0
}

private func isValidFittingLength(_ value: CGFloat) -> Bool {
    value > 0 &&
        value.isFinite &&
        value < CGFloat.greatestFiniteMagnitude / 2
}

@discardableResult
func manualLayoutVertical(
    views: [NSView],
    x: CGFloat = 0,
    y: CGFloat = 0,
    width: CGFloat,
    spacing: CGFloat
) -> CGFloat {
    guard !views.isEmpty else { return 0 }
    var currentY = y
    for (index, view) in views.enumerated() {
        let height = childHeight(of: view, for: width)
        view.frame = CGRect(x: x, y: currentY, width: width, height: height)
        currentY += height
        if index < views.count - 1 {
            currentY += spacing
        }
    }
    return currentY - y
}

func manualHeightForVertical(
    views: [NSView],
    width: CGFloat,
    spacing: CGFloat
) -> CGFloat {
    guard !views.isEmpty else { return 0 }
    var total: CGFloat = 0
    for (index, view) in views.enumerated() {
        total += childHeight(of: view, for: width)
        if index < views.count - 1 {
            total += spacing
        }
    }
    return total
}

enum ManualHorizontalAlignment {
    case top
    case center
    case fill
}

@discardableResult
func manualLayoutHorizontal(
    views: [NSView],
    x: CGFloat = 0,
    y: CGFloat = 0,
    height: CGFloat,
    spacing: CGFloat,
    alignment: ManualHorizontalAlignment = .top
) -> CGFloat {
    guard !views.isEmpty else { return 0 }
    var currentX = x
    for (index, view) in views.enumerated() {
        let width = childWidth(of: view, for: height)
        let viewHeight: CGFloat
        let viewY: CGFloat
        switch alignment {
        case .fill:
            viewHeight = height
            viewY = y
        case .center:
            viewHeight = childHeight(of: view, for: width)
            viewY = y + (height - viewHeight) / 2
        case .top:
            viewHeight = childHeight(of: view, for: width)
            viewY = y
        }
        view.frame = CGRect(x: currentX, y: viewY, width: width, height: viewHeight)
        currentX += width
        if index < views.count - 1 {
            currentX += spacing
        }
    }
    return currentX - x
}

final class TimelineTextField: NSTextField, TimelineHeightProviding {
    var maximumLines: Int = 0 {
        didSet {
            maximumNumberOfLines = maximumLines
            lineBreakMode = maximumLines == 1 ? .byTruncatingTail : .byWordWrapping
        }
    }

    init(
        font: NSFont = .preferredFont(forTextStyle: .body),
        color: NSColor = .labelColor,
        maximumLines: Int = 0
    ) {
        self.maximumLines = maximumLines
        super.init(frame: .zero)
        isEditable = false
        isSelectable = false
        isBordered = false
        drawsBackground = false
        cell?.wraps = true
        cell?.isScrollable = false
        lineBreakMode = maximumLines == 1 ? .byTruncatingTail : .byWordWrapping
        maximumNumberOfLines = maximumLines
        self.font = font
        textColor = color
        setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        setContentHuggingPriority(.defaultLow, for: .horizontal)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let attributed = attributedStringValue.length > 0
            ? attributedStringValue
            : NSAttributedString(
                string: stringValue,
                attributes: [
                    .font: font ?? NSFont.preferredFont(forTextStyle: .body),
                    .foregroundColor: textColor ?? NSColor.labelColor,
                ]
            )
        let rect = attributed.boundingRect(
            with: CGSize(width: width, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading]
        )
        let lineHeight = (font ?? .preferredFont(forTextStyle: .body)).flareLineHeight
        let maxHeight = maximumLines > 0 ? ceil(lineHeight * CGFloat(maximumLines)) : CGFloat.greatestFiniteMagnitude
        return min(ceil(rect.height) + 2, maxHeight + 2)
    }

    func preferredWidth(for height: CGFloat) -> CGFloat {
        let size = attributedStringValue.size()
        return max(ceil(size.width), 1)
    }
}

extension NSFont {
    var flareLineHeight: CGFloat {
        ceil(ascender - descender + leading)
    }
}
