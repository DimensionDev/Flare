#if canImport(AppKit)
import FlareUIRuntime
import AppKit

@MainActor
func makeFlareAppKitColumnView(
    payload: FlareUIColumnPayload,
    children: [FlareUINode],
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
    stack.orientation = .vertical
    stack.alignment = .leading
    stack.spacing = 0
    return stack
}
#endif
