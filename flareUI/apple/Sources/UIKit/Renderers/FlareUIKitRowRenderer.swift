@preconcurrency import FlareUIDemoKit
import UIKit

func makeFlareUIKitRowView(
    payload: FlareRowPayload,
    children: [FlareUiNodeSnapshot]
) -> UIView {
    let stack = UIStackView(
        arrangedSubviews: children.map {
            makeFlareUIKitNodeView(for: $0)
        }
    )
    stack.axis = .horizontal
    stack.alignment = .top
    stack.spacing = 0
    return stack
}
