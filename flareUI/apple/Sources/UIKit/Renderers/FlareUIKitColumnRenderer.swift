#if canImport(UIKit)
import FlareUIRuntime
import UIKit

@MainActor
func makeFlareUIKitColumnView(
    payload: FlareUIColumnPayload,
    children: [FlareUINode],
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
#endif
