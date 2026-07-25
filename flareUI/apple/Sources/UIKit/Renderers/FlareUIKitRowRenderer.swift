#if canImport(UIKit)
import FlareUIRuntime
import UIKit

@MainActor
func makeFlareUIKitRowView(
    payload: FlareUIRowPayload,
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
    stack.axis = .horizontal
    stack.alignment = .top
    stack.spacing = 0
    return stack
}
#endif
