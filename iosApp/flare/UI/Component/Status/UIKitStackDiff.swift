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
