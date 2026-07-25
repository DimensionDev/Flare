@preconcurrency import FlareUIDemoKit
import AppKit

func makeFlareAppKitColumnView(
    payload: FlareColumnPayload,
    children: [FlareUiNodeSnapshot]
) -> NSView {
    let stack = NSStackView(
        views: children.map {
            makeFlareAppKitNodeView(for: $0)
        }
    )
    stack.orientation = .vertical
    stack.alignment = .leading
    stack.spacing = 0
    return stack
}
