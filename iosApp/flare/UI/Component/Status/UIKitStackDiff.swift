import UIKit

extension UIStackView {
    func flareSyncArrangedSubviews(_ desired: [UIView]) {
        let desiredIDs = Set(desired.map { ObjectIdentifier($0) })
        for view in arrangedSubviews where !desiredIDs.contains(ObjectIdentifier(view)) {
            removeArrangedSubview(view)
            view.removeFromSuperview()
        }

        for (index, target) in desired.enumerated() {
            let current = arrangedSubviews
            if current.indices.contains(index), current[index] === target {
                continue
            }
            if current.contains(where: { $0 === target }) {
                removeArrangedSubview(target)
            }
            insertArrangedSubview(target, at: min(index, arrangedSubviews.count))
        }
    }
}

// MARK: - Manual Layout Helpers

protocol ManualLayoutMeasurable: AnyObject {}

protocol TimelineHeightProviding: AnyObject {
    func timelineHeight(for width: CGFloat) -> CGFloat?
}

/// Syncs `current` managed subview array to match `desired`, adding/removing from `parent`.
func syncManagedSubviews(parent: UIView, current: inout [UIView], desired: [UIView]) {
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

/// Query a child view's preferred height for a given width.
/// Works for both manually-laid-out views (sizeThatFits) and constraint-based views (systemLayoutSizeFitting).
func childHeight(of view: UIView, for width: CGFloat) -> CGFloat {
    guard width > 0, width.isFinite else { return 0 }

    if let provider = view as? TimelineHeightProviding,
       let height = provider.timelineHeight(for: width),
       isValidFittingLength(height) {
        return ceil(height)
    }

    if view is ManualLayoutMeasurable {
        let manualSize = view.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        if isValidFittingLength(manualSize.height) {
            return ceil(manualSize.height)
        }
    }

    let systemSize = view.systemLayoutSizeFitting(
        CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
        withHorizontalFittingPriority: .required,
        verticalFittingPriority: .fittingSizeLevel
    )
    if isValidFittingLength(systemSize.height) {
        return ceil(systemSize.height)
    }

    return 0
}

private func isValidFittingLength(_ value: CGFloat) -> Bool {
    value > 0 &&
        value.isFinite &&
        value != UIView.noIntrinsicMetric &&
        value < CGFloat.greatestFiniteMagnitude / 2
}

/// Query a child view's preferred width for a given height.
func childWidth(of view: UIView, for height: CGFloat) -> CGFloat {
    if view is ManualLayoutMeasurable {
        let size = view.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: height))
        if size.width > 0,
           size.width.isFinite,
           size.width != UIView.noIntrinsicMetric {
            return ceil(size.width)
        }
    }

    let systemSize = view.systemLayoutSizeFitting(
        CGSize(width: UIView.layoutFittingCompressedSize.width, height: height),
        withHorizontalFittingPriority: .fittingSizeLevel,
        verticalFittingPriority: .required
    )
    if systemSize.width > 0,
       systemSize.width.isFinite,
       systemSize.width != UIView.noIntrinsicMetric {
        return ceil(systemSize.width)
    }

    let size = view.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: height))
    if size.width > 0,
       size.width.isFinite,
       size.width != UIView.noIntrinsicMetric {
        return ceil(size.width)
    }

    return 0
}

/// Lays out views vertically, assigning frames. Returns total height consumed.
@discardableResult
func manualLayoutVertical(
    views: [UIView],
    x: CGFloat = 0,
    y: CGFloat = 0,
    width: CGFloat,
    spacing: CGFloat
) -> CGFloat {
    guard !views.isEmpty else { return 0 }
    var currentY = y
    for (i, view) in views.enumerated() {
        let h = childHeight(of: view, for: width)
        view.frame = CGRect(x: x, y: currentY, width: width, height: h)
        currentY += h
        if i < views.count - 1 {
            currentY += spacing
        }
    }
    return currentY - y
}

/// Computes total height for vertical layout without assigning frames.
func manualHeightForVertical(
    views: [UIView],
    width: CGFloat,
    spacing: CGFloat
) -> CGFloat {
    guard !views.isEmpty else { return 0 }
    var total: CGFloat = 0
    for (i, view) in views.enumerated() {
        total += childHeight(of: view, for: width)
        if i < views.count - 1 {
            total += spacing
        }
    }
    return total
}

enum ManualHorizontalAlignment {
    case top, center, fill
}

/// Lays out views horizontally, assigning frames. Returns total width consumed.
@discardableResult
func manualLayoutHorizontal(
    views: [UIView],
    x: CGFloat = 0,
    y: CGFloat = 0,
    height: CGFloat,
    spacing: CGFloat,
    alignment: ManualHorizontalAlignment = .top
) -> CGFloat {
    guard !views.isEmpty else { return 0 }
    var currentX = x
    for (i, view) in views.enumerated() {
        let w = childWidth(of: view, for: height)
        let viewH: CGFloat
        let viewY: CGFloat
        switch alignment {
        case .fill:
            viewH = height
            viewY = y
        case .center:
            viewH = childHeight(of: view, for: w)
            viewY = y + (height - viewH) / 2
        case .top:
            viewH = childHeight(of: view, for: w)
            viewY = y
        }
        view.frame = CGRect(x: currentX, y: viewY, width: w, height: viewH)
        currentX += w
        if i < views.count - 1 {
            currentX += spacing
        }
    }
    return currentX - x
}
