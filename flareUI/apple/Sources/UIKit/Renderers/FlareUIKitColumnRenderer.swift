@preconcurrency import FlareUIDemoKit
import UIKit

func makeFlareUIKitColumnView(
    payload: FlareColumnPayload,
    children: [FlareUiNodeSnapshot],
    resources: FlareAppleResources
) -> UIView {
    let stack = UIStackView(
        arrangedSubviews: children.map {
            makeFlareUIKitNodeView(
                for: $0,
                resources: resources
            )
        }
    )
    stack.axis = .vertical
    stack.alignment = .leading
    stack.spacing = 0
    return stack
}
