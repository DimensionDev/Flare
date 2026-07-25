#if canImport(AppKit)
import FlareUIRuntime
import AppKit

@MainActor
func makeFlareAppKitTextView(
    payload: FlareUITextPayload,
    children: [FlareUINode],
    resources: FlareAppleResources
) -> NSView {
    NSTextField(
        wrappingLabelWithString: resources.string(payload.value)
    )
}
#endif
