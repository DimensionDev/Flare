@preconcurrency import FlareUIDemoKit
import AppKit

func makeFlareAppKitRowView(
    payload: FlareRowPayload,
    children: [FlareUiNodeSnapshot],
    resources: FlareAppleResources
) -> NSView {
    let stack = NSStackView(
        views: children.map {
            makeFlareAppKitNodeView(
                for: $0,
                resources: resources
            )
        }
    )
    stack.orientation = .horizontal
    stack.alignment = .top
    stack.spacing = 0
    return stack
}
