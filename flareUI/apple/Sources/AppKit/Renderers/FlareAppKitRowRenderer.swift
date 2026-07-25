#if canImport(AppKit)
import FlareUIRuntime
import AppKit

@MainActor
func makeFlareAppKitRowView(
    payload: FlareUIRowPayload,
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
    stack.orientation = .horizontal
    stack.alignment = .top
    stack.spacing = 0
    return stack
}
#endif
