@preconcurrency import FlareUIDemoKit
import AppKit

func makeFlareAppKitRowView(
    payload: FlareRowPayload,
    children: [FlareUiNodeSnapshot]
) -> NSView {
    let stack = NSStackView(
        views: children.map {
            makeFlareAppKitNodeView(for: $0)
        }
    )
    stack.orientation = .horizontal
    stack.alignment = .top
    stack.spacing = 0
    return stack
}
